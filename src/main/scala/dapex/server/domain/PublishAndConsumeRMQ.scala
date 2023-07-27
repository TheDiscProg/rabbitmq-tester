package dapex.server.domain

import cats.effect.Temporal
import cats.implicits._
import dapex.rabbitmq.consumer.{DapexMQConsumer, DapexMessageHandler}
import dapex.rabbitmq.publisher.DapexMQPublisher
import dapex.server.domain.rmq.RMQPublisher
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import fs2._
import org.typelevel.log4cats.Logger
object PublishAndConsumeRMQ {

  def run[F[_]: Temporal: Logger](
      rmqClient: RabbitClient[F],
      handlers: List[DapexMessageHandler[F]],
      rmqPublisher: DapexMQPublisher[F],
      channel: AMQPChannel
  ): F[Unit] = {
    val consumers: fs2.Stream[F, Unit] =
      DapexMQConsumer.consumerRMQStream(rmqClient, handlers, channel)
    val publishers: Stream[F, Int] = RMQPublisher.publish(rmqPublisher, handlers.map(_.queue))
    val merged: Stream[F, Any] = Stream(consumers, publishers).parJoinUnbounded
    for {
      _ <- Logger[F].info(s"Running streams")
      _ <- merged.compile.drain
    } yield ()
  }

}
