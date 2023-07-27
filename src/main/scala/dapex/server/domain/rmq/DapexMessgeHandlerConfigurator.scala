package dapex.server.domain.rmq

import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.consumer.DapexMessageHandler
import org.typelevel.log4cats.Logger

object DapexMessgeHandlerConfigurator {

  def getHandlers[F[_]: Logger](): Vector[DapexMessageHandler[F]] = {
    val consumer = RMQConsumer()
    Vector(
      DapexMessageHandler(RabbitQueue.SERVICE_AUTHENTICATION_QUEUE, consumer.handleAuthQueue),
      DapexMessageHandler(RabbitQueue.SERVICE_DBREAD_QUEUE, consumer.handleDBReadQueue),
      DapexMessageHandler(
        RabbitQueue.SERVICE_COLLECTION_POINT_QUEUE,
        consumer.handleCollectionQueue
      )
    )
  }
}
