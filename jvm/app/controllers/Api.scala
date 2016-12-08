package controllers

import java.time.{Duration, Instant}
import javax.inject.{Inject, _}

import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Source}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.typesafe.scalalogging.LazyLogging
import ophan.consumption._
import ophan.thrift.event.{Event, PageView}
import play.api.libs.EventSource
import play.api.mvc._
import shared.AutowiredApi
import upickle._
import upickle.default._

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
  eventConsumerFactory: EventConsumerFactory
)(implicit actorSystem: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext) extends Controller with shared.AutowiredApi with LazyLogging {

  val (sink, source) =
    MergeHub.source[String](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()

  def handleNewEvents(events: Seq[Event]) {
    val now = Instant.now
    val threshold = now.minusSeconds(60)
    // println(events.size)
    val (oldEvents, newEvents): ((Seq[Event], Seq[Event])) = events.partition(_.instant.isBefore(threshold))
    if (oldEvents.nonEmpty) {
      println(s"Disgarding ${oldEvents.size} because they're too old")
    }
    val tuples: Seq[(Event, PageView)] = newEvents.flatMap(e => e.pageView.filter(_.page.url.path.contains("developer-blog")).map(pv => (e, pv)))

    tuples.foreach { case (e, pv) =>
      val realtimeLatency = Duration.between(e.instant, now)

      println(s"${pv.page.url.path} - $realtimeLatency")
      makeAHit()
    }
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


  def mixedStream = Action {
    Ok.chunked(source via EventSource.flow)
  }

  override def makeAHit(): Future[String] = {
    Source.single("Whatever").runWith(sink)

    Future.successful("Totally sent it")
  }


}
