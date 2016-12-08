package ophan.consumption

import javax.inject.{Inject, Singleton}

import com.amazonaws.services.kinesis.clientlibrary.interfaces.{IRecordProcessor, IRecordProcessorFactory}
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker
import ophan.thrift.event.Event
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EventConsumerFactory @Inject()(
  lifecycle: ApplicationLifecycle
) {

  def start(listener: Seq[Event] => Unit) = Future {
    // Create a worker, which will in turn create one or more EventProcessors
    val worker = new Worker(new IRecordProcessorFactory {
      override def createProcessor(): IRecordProcessor = new EventRecordProcessor(listener)
    }, OphanEventStreamKinesisConfig.config)

    worker.run()
    lifecycle.addStopHook { () =>
      Future.successful(worker.shutdown())
    }
  }

}
