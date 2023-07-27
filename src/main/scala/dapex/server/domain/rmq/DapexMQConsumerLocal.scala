package dapex.server.domain.rmq

import cats.effect.{Concurrent, ExitCode, Temporal}
import cats.implicits._
import dapex.messaging.DapexMessage
import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.consumer.{DapexMQConsumerAlgebra, DapexMessageConsumer, DapexMessageHandler}
import dapex.rabbitmq.entities.ServiceError
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.config.declaration._
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck}
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AckResult, AmqpEnvelope, DeliveryTag}
import dev.profunktor.fs2rabbit.resiliency.ResilientStream
import fs2.Stream
import io.circe.{Decoder, Error}
import org.typelevel.log4cats.Logger

class DapexMQConsumerLocal[F[_]: Concurrent: Logger](rmqClient: RabbitClient[F])(implicit
    channel: AMQPChannel
) extends DapexMQConsumerAlgebra[F] {

  private lazy val fs2JsonDecoder: Fs2JsonDecoder = new Fs2JsonDecoder

  override def consumeRMQDapexMessage(handlers: List[DapexMessageHandler[F]]): Stream[F, Unit] = {
    val consumers = setUpRMQConsumers(handlers)
    createConsumerStream(consumers)
  }

  private def createConsumerStream(consumers: F[List[DapexMessageConsumer[F]]]): Stream[F, Unit] =
    (for {
      consumer: DapexMessageConsumer[F] <- Stream.evalSeq(consumers)
      (acker, stream) = consumer.ackerConsumer
    } yield stream
      .through(decoder[DapexMessage])
      .flatMap {
        handleDapexMessage(_)(acker)(consumer.handler.f)
      }).parJoinUnbounded

  private def setUpRMQConsumers(
      handlers: List[DapexMessageHandler[F]]
  ): F[List[DapexMessageConsumer[F]]] =
    for {
      _ <- createExchangeAndQueues(handlers.map(_.queue))
      handlers <- handlers.map { handler =>
        for {
          consumer <- getAckerConsumerForQueue(handler)
        } yield consumer
      }.sequence
    } yield handlers

  private def createExchangeAndQueues(queues: List[RabbitQueue]): F[Unit] =
    for {
      _ <- createExchanges(queues)
      _ <- createQueues(queues)
    } yield ()

  private def createExchanges(queues: List[RabbitQueue]): F[Unit] =
    queues.toVector
      .traverse(queue => createExchange(queue)) *>
      ().pure[F]

  private def createExchange(queue: RabbitQueue): F[Unit] =
    for {
      _ <- Logger[F].info(s"Creating Exchange for ${queue.exchange}")
      conf = DeclarationExchangeConfig
        .default(queue.exchange, queue.exchangeType)
        .copy(durable = Durable)
      _ <- rmqClient.declareExchange(conf)
    } yield ()

  private def createQueues(queues: List[RabbitQueue]): F[Unit] =
    queues.toVector
      .traverse(queue => createQueue(queue)) *>
      ().pure[F]

  private def createQueue(queue: RabbitQueue): F[Unit] =
    for {
      _ <- Logger[F].info(s"Creating Consumer queue [${queue.name}]")
      queueConfig = DeclarationQueueConfig(
        queueName = queue.name,
        durable = Durable,
        exclusive = NonExclusive,
        autoDelete = NonAutoDelete,
        arguments = getArgumentsForQueue(queue.dlx, queue.messageTTL)
      )
      _ <- rmqClient.declareQueue(queueConfig)
      _ <- rmqClient.bindQueue(
        queueName = queue.name,
        exchangeName = queue.exchange,
        routingKey = queue.routingKey
      )
    } yield ()

  private def getArgumentsForQueue(dlx: Option[String], messageTTL: Option[Long]): Arguments =
    (dlx, messageTTL) match {
      case (Some(dlx), Some(ttl)) =>
        Map("x-dead-letter-exchange" -> dlx, "x-message-ttl" -> ttl): Arguments
      case (Some(dlx), None) =>
        Map("x-dead-letter-exchange" -> dlx): Arguments
      case (None, Some(ttl)) =>
        Map("x-message-ttl" -> ttl): Arguments
      case _ => Map.empty: Arguments
    }

  private def getAckerConsumerForQueue(
      handler: DapexMessageHandler[F]
  ): F[DapexMessageConsumer[F]] =
    for {
      ca <- rmqClient.createAckerConsumer[String](handler.queue.name)
    } yield DapexMessageConsumer(handler = handler, ackerConsumer = ca)

  private def handleDapexMessage[E <: ServiceError](
      decodedMsg: (Either[Error, DapexMessage], DeliveryTag)
  )(acker: AckResult => F[Unit])(f: DapexMessage => F[Unit]): Stream[F, Unit] =
    decodedMsg match {
      case (Left(error), tag) =>
        Stream
          .eval(Logger[F].warn(error.getMessage))
          .map(_ => NAck(tag))
          .evalMap(acker)
      case (Right(msg), tag) =>
        Stream
          .eval(f(msg))
          .map(_ => Ack(tag))
          .evalMap(acker)
    }

  private def decoder[A <: DapexMessage: Decoder]
      : Stream[F, AmqpEnvelope[String]] => Stream[F, (Either[Error, A], DeliveryTag)] =
    _.map(fs2JsonDecoder.jsonDecode[A])

}

object DapexMQConsumerLocal {

  def consumeRMQ[F[_]: Log: Temporal: Logger](
      rmqClient: RabbitClient[F],
      handlers: List[DapexMessageHandler[F]],
      channel: AMQPChannel
  ): F[ExitCode] = {
    implicit val c = channel
    val dapexMQConsumer = new DapexMQConsumerLocal[F](rmqClient)
    ResilientStream
      .run(dapexMQConsumer.consumeRMQDapexMessage(handlers))
      .as(ExitCode.Success)
  }

  def consumerRMQStream[F[_]: Temporal: Logger](
      rmqClient: RabbitClient[F],
      handlers: List[DapexMessageHandler[F]],
      channel: AMQPChannel
  ): Stream[F, Unit] = {
    implicit val c = channel
    val dapexMQConsumer = new DapexMQConsumerLocal[F](rmqClient)
    dapexMQConsumer.consumeRMQDapexMessage(handlers)
  }

}
