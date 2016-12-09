package controllers

import java.time.{Duration, Instant}
import javax.inject.{Inject, _}

import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Source}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.google.common.util.concurrent.AtomicLongMap
import com.gu.contentapi.client.model.v1.Tag
import com.typesafe.scalalogging.LazyLogging
import ophan.consumption._
import ophan.thrift.event.{Event, PageView}
import play.api.http.ContentTypes
import play.api.libs.EventSource
import play.api.libs.EventSource.EventDataExtractor
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import shared.{AutowiredApi, BattleState}
import upickle._
import upickle.default._
import util.MonitoredTags

import scala.collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}


object AWSCredentialsProvider {
  val Dev = new ProfileCredentialsProvider("developerPlayground")
  val Prod = new InstanceProfileCredentialsProvider(false)
  val Chain = new AWSCredentialsProviderChain(Dev, Prod)
}

object AutowireServer extends autowire.Server[Js.Value, Reader, Writer] {
  def read[Result: Reader](p: Js.Value) = upickle.default.readJs[Result](p)

  def write[Result: Writer](r: Result) = upickle.default.writeJs(r)
}

@Singleton
class Api @Inject()(
  eventConsumerFactory: EventConsumerFactory,
  monitoredTags: MonitoredTags
)(implicit actorSystem: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext) extends Controller with shared.AutowiredApi with LazyLogging {

  val (sink, source) =
    MergeHub.source[BattleState](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()

  eventConsumerFactory.start(handleNewEvents)

  val count = AtomicLongMap.create[String]()

  def handleNewEvents(events: Seq[Event]) {
    val now = Instant.now
    val threshold = now.minusSeconds(120)
    // println(events.size)
    val (oldEvents, newEvents): ((Seq[Event], Seq[Event])) = events.partition(_.instant.isBefore(threshold))
    if (oldEvents.nonEmpty) {
      println(s"Discarding ${oldEvents.size} because they're too old")
    }
    val tuples: Seq[(Event, PageView)] = newEvents.flatMap(e => e.pageView.filter(_.page.url.path.contains("developer-blog")).map(pv => (e, pv)))

    tuples.foreach { case (e, pv) =>
      val realtimeLatency = Duration.between(e.instant, now)

      println(s"${pv.page.url.path} - $realtimeLatency")
    }

    for (interestingContent <- monitoredTags.interestingContent) {
      val scoredTags: Seq[Tag] = for {
        event <- newEvents
        pageView <- event.pageView.toSeq
        interestingTags <- interestingContent.get(pageView.page.url.path).toSeq
        tag <- interestingTags
      } yield tag
      val countsByTag = scoredTags.groupBy(_.id).mapValues(_.size)
      for {
        (tagId, tagCount) <- countsByTag
      } {
        count.addAndGet(tagId, tagCount)
      }
      if (countsByTag.nonEmpty) {
        send(battleState())
      }
    }
  }

  private def battleState() = {
    BattleState(count.asMap().toMap.mapValues(_.toLong))
  }

  def autowireApi(path: String) = Action.async(parse.json) { implicit request =>
    val autowireRequest = autowire.Core.Request(
      path.split("/"),
      upickle.json.read(request.body.toString()).asInstanceOf[Js.Obj].value.toMap
    )

    AutowireServer.route[AutowiredApi](this)(autowireRequest).map(responseJS => {
      Ok(upickle.json.write(responseJS))
    })
  }


  implicit val jsonEvents: EventDataExtractor[BattleState] = EventDataExtractor(bs=> upickle.default.write(bs))

  def mixedStream = Action {
    Ok.chunked(source via EventSource.flow).as(ContentTypes.EVENT_STREAM)
  }

  override def getBattleState(): Future[BattleState] = {
    Future.successful(battleState())
  }

  override def makeAHit(): Future[String] = {
    count.clear()
//
//    send(BattleState(
//      Map[String, Long](
//        "roberto-tyley" -> scala.util.Random.nextInt(50),
//        "phil-wills" -> scala.util.Random.nextInt(50)
//      )
//    ))

    Future.successful("Didn't send it")
  }


  private def send(state: BattleState) = {
    Source.single(state).runWith(sink)
  }
}
