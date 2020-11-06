package com.quasigroup.zio.akka

import akka.actor.{ActorRef, ActorSelection, ActorSystem, Cancellable, Props}
import zio._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

package object classic {

  type ClassicAkka = Has[ActorSystem]
  type ClassicAkkaService = Has[Service]

  trait Service {
    def register(props: Props, name: String): Task[ActorRef]
    def register(props: Props): Task[ActorRef]
    def select(path: String): Task[ActorSelection]
    def stop(actor: ActorRef): Task[Unit]
    def scheduleOnce(delay: FiniteDuration, f: => Unit): Task[Cancellable]
    def scheduleOnce(
        delay: FiniteDuration,
        receiver: ActorRef,
        message: Any
    ): Task[Unit]
  }

  val demo = live("demo")

  def live(name: String): ZLayer[Any, Throwable, ClassicAkka] = {
    start(name).toLayer
  }

  def start(name: String): ZManaged[Any, Throwable, ActorSystem] = {
    Task
      .effect(ActorSystem(name))
      .toManaged(sys => Task.fromFuture(_ => sys.terminate()).either)
  }

  def createService(system: ActorSystem): Task[Service] = Task.effect {
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    new classic.Service {
      def register(props: Props, name: String): Task[ActorRef] =
        Task { system.actorOf(props, name) }
      def register(props: Props): Task[ActorRef] =
        Task { system.actorOf(props) }
      def select(path: String): Task[ActorSelection] =
        Task { system.actorSelection(path) }
      def stop(actor: ActorRef): Task[Unit] =
        Task { system.stop(actor) }
      def scheduleOnce(delay: FiniteDuration, f: => Unit): Task[Cancellable] =
        Task { system.scheduler.scheduleOnce(delay)(f) }
      def scheduleOnce(
          delay: FiniteDuration,
          receiver: ActorRef,
          message: Any
      ): Task[Unit] =
        Task { system.scheduler.scheduleOnce(delay, receiver, message) }
    }
  }

  val serviceM: ZManaged[ActorSystem, Throwable, Service] =
    ZManaged.fromEffect(ZIO.environment[ActorSystem] >>= createService)

  val service: ZLayer[ClassicAkka, Throwable, ClassicAkkaService] =
    ZLayer.fromServiceM(createService)

  def register(props: Props): RIO[ClassicAkkaService, ActorRef] =
    ZIO.accessM(_.get.register(props))

  def register(props: Props, name: String): RIO[ClassicAkkaService, ActorRef] =
    ZIO.accessM(_.get.register(props, name))

  def select(path: String): RIO[ClassicAkkaService, ActorSelection] =
    ZIO.accessM(_.get.select(path))

  def stop(actor: ActorRef): RIO[ClassicAkkaService, Unit] =
    ZIO.accessM(_.get.stop(actor))

  def scheduleOnce(
      delay: FiniteDuration,
      f: => Unit
  ): RIO[ClassicAkkaService, Cancellable] =
    ZIO.accessM(_.get.scheduleOnce(delay, f))

  def scheduleOnce(
      delay: FiniteDuration,
      receiver: ActorRef,
      message: Any
  ): RIO[ClassicAkkaService, Unit] =
    ZIO.accessM(_.get.scheduleOnce(delay, receiver, message))
}
