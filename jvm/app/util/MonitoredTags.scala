package util

import java.time.Instant
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.gu.contentapi.client.GuardianContentClient
import com.gu.contentapi.client.model.SearchQuery
import com.gu.contentapi.client.model.v1.Tag
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class MonitoredTags @Inject()(implicit system: ActorSystem) extends LazyLogging {

  val client = new GuardianContentClient(Config.contentApiKey)

  val interestingContent: Future[Map[String, Seq[Tag]]] = for {
    result <- client.getResponse(SearchQuery().tag("info/developer-blog").pageSize(200).showTags("contributor"))
  } yield {
    val m = result.results.map { c =>
      s"/${c.id}" -> c.tags
    }.toMap

    val tags = m.values.flatten.toSet

    logger.info(s"Found ${m.size} articles, by ${tags.size} authors (${tags.take(3).map(_.id)})")
    m
  }
}
