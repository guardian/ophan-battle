package ophan.consumption

import java.time.{Duration, Instant}
import java.util.{List => JList}

import com.amazonaws.services.kinesis.clientlibrary.interfaces.{IRecordProcessor, IRecordProcessorCheckpointer}
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason
import com.amazonaws.services.kinesis.model.Record
import com.typesafe.scalalogging.LazyLogging
import ophan.thrift.event.Event

import scala.collection.convert.wrapAll._

class EventRecordProcessor(listener: Seq[Event] => Unit) extends IRecordProcessor with LazyLogging {
  private[this] var shardId: String = _

  override def initialize(shardId: String): Unit = {
    this.shardId = shardId
    logger.info(s"Initialized an event processor for shard $shardId")
  }

  override def processRecords(records: JList[Record], checkpointer: IRecordProcessorCheckpointer): Unit = {
    val allEvents = records.map(deserializeToEvent)
    val earliestEvent = allEvents.minBy(_.instant).instant
    val latestEvent = allEvents.maxBy(_.instant).instant
    val spanDuration = Duration.between(earliestEvent, latestEvent)
    val realtimeLatency = Duration.between(latestEvent, Instant.now)
    logger.info(s"received ${allEvents.size}/${records.size} events, spanning $spanDuration, most recent event at $latestEvent, latency $realtimeLatency")
    listener(allEvents)
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
    logger.warn(s"Shutdown event processor for shard $shardId because $reason")
  }
}