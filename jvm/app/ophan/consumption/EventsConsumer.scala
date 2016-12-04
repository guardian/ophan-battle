package ophan.consumption

import java.time.Instant
import java.util.{List => JList}
import javax.inject.{Inject, Singleton}

import com.amazonaws.services.kinesis.clientlibrary.interfaces.{IRecordProcessor, IRecordProcessorCheckpointer, IRecordProcessorFactory}
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.{KinesisClientLibConfiguration, ShutdownReason, Worker}
import com.amazonaws.services.kinesis.model.Record
import ophan.thrift.event.Event
import play.api.inject.ApplicationLifecycle
import EventsConsumer._

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EventsConsumer @Inject() (
  eventStreamKinesisConfig: KinesisClientLibConfiguration,
  lifecycle: ApplicationLifecycle
) {

  // Create a worker, which will in turn create one or more EventProcessors
  val worker = new Worker(new IRecordProcessorFactory {
    override def createProcessor(): IRecordProcessor = new IRecordProcessor {
      private[this] var shardId: String = _

      override def initialize(shardId: String): Unit = {
        this.shardId = shardId
        println(s"Initialized an event processor for shard $shardId")
      }

      override def processRecords(records: JList[Record], checkpointer: IRecordProcessorCheckpointer): Unit = {
        println(s"got ${records.size()}")
        val allEvents = records.map(deserializeToEvent)
        val earliestEvent = allEvents.minBy(_.dt).instant
        val latestEvent = allEvents.maxBy(_.dt).instant
        println(s"received ${allEvents.size} events, earliest event at $earliestEvent, most recent event at $latestEvent")

        // val relevantPageViews = allEvents.flatMap(relevantPageView)
        checkpointer.checkpoint()
      }


      def deserializeToEvent(record: Record): Event =
        ThriftSerializer.fromByteBuffer(record.getData)(Event.decoder)

      // This method may be called by KCL, e.g. in case of shard splits/merges
      override def shutdown(checkpointer: IRecordProcessorCheckpointer, reason: ShutdownReason): Unit = {
        if (reason == ShutdownReason.TERMINATE) {
          checkpointer.checkpoint()
        }
        println(s"Shutdown event processor for shard $shardId because $reason")
      }
    }
  }, eventStreamKinesisConfig)

  def start() = Future {
    worker.run()
    lifecycle.addStopHook { () =>
      Future.successful(worker.shutdown())
    }
  }

}

object EventsConsumer {
  implicit class RichEvent(event: Event) {
    lazy val instant = Instant.ofEpochMilli(event.dt)
  }
}