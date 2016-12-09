import autowire._
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{ReactComponentB, ReactDOM}
import org.scalajs.dom.ext.Ajax
import shared.{AutowiredApi, BattleState}
import upickle.Js
import upickle.default.{readJs, writeJs, _}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport


object Ajaxer extends autowire.Client[Js.Value, Reader, Writer]{
  override def doCall(req: Request): Future[Js.Value] = {
    val url = "/api/" + req.path.mkString("/")
    val jsonPayload = upickle.json.write(Js.Obj(req.args.toSeq: _*))

    Ajax.post(url, jsonPayload, 0,
      Map("X-Requested-With" -> "XMLHttpRequest",
        "Content-Type" -> "application/json"), withCredentials = true)
      .map(_.responseText)
      .map(upickle.json.read)
  }

  def read[Result: Reader](p: Js.Value) = readJs[Result](p)
  def write[Result: Writer](r: Result) = writeJs(r)
}

object Main extends js.JSApp {
  def main(): Unit = {
    import org.scalajs.dom

    val eventSource = new dom.EventSource("/boom")

    val comp = ReactComponentB[BattleState]("MyComponent").render_P {
      bs =>
        val playerDivs = for {
          (contestantId, score) <- bs.scores.toSeq.sortBy(_._2).reverse
        } yield {
          <.h1(^.`class` :="display-2",
            contestantId+": ", <.b(score)
          )
        }

        <.div(playerDivs)
    }.build


    ReactDOM.render(comp(BattleState(Map.empty)), dom.document.getElementById("root"))

    eventSource.onmessage = {
      (message: dom.MessageEvent) =>
        val state = Ajaxer.read[BattleState](upickle.json.read(message.data.toString))

        ReactDOM.render(comp(state),  dom.document.getElementById("root")) // ha, this is probably wrong, right?!
    }
  }

  @JSExport
  def addClickedMessage() {
    Ajaxer[AutowiredApi].makeAHit().call()
  }
}
