package com.quasigroup.zio.akka.classic

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import com.quasigroup.zio.akka.classic
import com.quasigroup.zio.akka.models._
import zio._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

package object http {
  type Binding = Has[ServerBinding]
  type ToRoute = ExecutionContext => Route

  def start(bindingOn: BindOn, withRoutes: ToRoute): ZManaged[ActorSystem, Throwable, ServerBinding] =
    (for {
      system <- ZIO.environment[ActorSystem]
      server <- Task.fromFuture {
        implicit val sys: ActorSystem = system
        Http()
          .newServerAt(bindingOn.host, bindingOn.port)
          .bindFlow(withRoutes(sys.dispatcher))
          .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))(_)
      }
    } yield server).toManaged_
  import akka.http.scaladsl.server.Directives._

  val demo: ZLayer[Any, Throwable, Binding] =
    classic.demo >>> live(
      BindOn("127.0.0.1", 8080),
      _ =>
        get {
          pathSingleSlash {
            complete {
              "It works!"
            }
          }
        }
    )

  def live(bindingOn: BindOn, withRoutes: ToRoute): ZLayer[ClassicAkka, Throwable, Binding] =
    ZLayer.fromServiceManaged(start(bindingOn, withRoutes).provide)

}
