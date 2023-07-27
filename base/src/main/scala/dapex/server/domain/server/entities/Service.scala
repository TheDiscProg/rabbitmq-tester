package dapex.server.domain.server.entities

import dapex.rabbitmq.consumer.DapexMessageHandler
import dapex.rabbitmq.publisher.DapexMQPublisher
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import org.http4s.server.Server

case class Service[F[_]](
    server: Server,
    rmqClient: RabbitClient[F],
    handlers: List[DapexMessageHandler[F]],
    rmqPublisher: DapexMQPublisher[F],
    channel: AMQPChannel
)
