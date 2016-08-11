package akka.wamp

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.ws.TextMessage.Strict
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.{ConnectionContext, Http, HttpExt}
import akka.io._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestActorRef, TestProbe}
import akka.wamp.Wamp._
import akka.wamp.messages.{Subscribe, Subscribed}
import akka.wamp.router.Router
import org.scalatest.ParallelTestExecution
import org.scalatest.mock.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration._
import org.mockito.Mockito._
import org.mockito.Matchers._


// reference.conf is overriding akka.wamp.router.port to enable dynamic port bindings
class ManagerSpec 
  extends ActorSpec(ActorSystem("test"))
  with ParallelTestExecution
    with MockitoSugar {

  "The IO(Wamp) manager" should "bind router" in { f =>
      val manager = IO(Wamp)
      manager ! Bind(f.router)
      f.probe.expectMsgType[Bound](8 seconds)
  }

  it should "connect client" in { f =>
    val manager = IO(Wamp)
    manager ! Bind(f.router)
    val bound = f.probe.expectMsgType[Bound](8 seconds)
    val address = s"${bound.localAddress.getHostString}:${bound.localAddress.getPort}"

    // connect the router
    manager ! Connect(client = testActor, url = s"ws://$address/ws")
    val connected = expectMsgType[Wamp.Connected](8 seconds)
    connected.peer must not be (null)
  }
  
  it should "unbind router" in { f =>
    pending
  }

  it should "disconnect client" in { f =>
    pending
  }

  it should "Fail on wrong message format" in { f =>
    val manager = mockHttpSetup(TextMessage("hello world!"))

    // connect the router
    manager ! Connect(client = testActor, url = s"ws://test/ws")
    val failure = expectMsgType[Status.Failure](8 seconds)
    failure.cause.getMessage must include("hello world")
  }


  // see http://www.scalatest.org/user_guide/sharing_fixtures#withFixtureNoArgTest
  /*override def withFixture(test: NoArgTest) = {
    // Perform setup
    try super.withFixture(test) // Invoke the test function
    finally {
      // Perform cleanup
    }
  }*/

  
  // see http://www.scalatest.org/user_guide/sharing_fixtures#loanFixtureMethods
  /*def withProbedRouter(testCode: (TestActorRef[Router], TestProbe) => Any) = {
    val probe = TestProbe("listener")
    val router = TestActorRef[Router](Router.props(listener = Some(probe.ref)))
    testCode(router, probe)
    system.stop(router)
  }*/
  
  
  // see http://www.scalatest.org/user_guide/sharing_fixtures#withFixtureOneArgTest
  case class FixtureParam(router: TestActorRef[Router], probe: TestProbe)
  def withFixture(test: OneArgTest) = {
    val probe = TestProbe()
    val router = TestActorRef[Router](Router.props(listener = Some(probe.ref)))
    val theFixture = FixtureParam(router, probe)
    try {
      withFixture(test.toNoArgTest(theFixture)) // "loan" the fixture to the test
    }
    finally {
      system.stop(probe.ref)
      system.stop(router)
    }
  }

  def mockHttpSetup(textMessage: TextMessage*) = {
    val http = mock[HttpExt]

    val printSink: Sink[Message, Future[WebSocketUpgradeResponse]] =
      Sink.fold(mock[WebSocketUpgradeResponse]) {
        case (u, t) => println(t);u
      }

    val helloSource: Source[Message, NotUsed] =
      Source.fromIterator(() => textMessage.toIterator)

    val flow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
      Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.left)

    when(http.webSocketClientFlow(
      any[WebSocketRequest],
      any[ConnectionContext],
      any[Option[InetSocketAddress]],
      any[ClientConnectionSettings],
      any[LoggingAdapter])).thenReturn(flow)
    IO(Wamp(http))
  }
}
