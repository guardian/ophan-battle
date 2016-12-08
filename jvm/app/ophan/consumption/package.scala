package ophan

import java.time.Instant

import ophan.thrift.event.Event

package object consumption {

  implicit class RichEvent(event: Event) {
    lazy val instant = Instant.ofEpochMilli(event.receivedDt)
  }
}
