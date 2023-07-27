package dapex.server.domain.rmq

import dapex.messaging.DapexMessage
import org.typelevel.log4cats.Logger

class RMQConsumer[F[_]: Logger]() {

  def handleAuthQueue(msg: DapexMessage): F[Unit] =
    logMessage("Authentication", msg)

  def handleDBReadQueue(msg: DapexMessage): F[Unit] =
    logMessage("DBRead", msg)

  def handleCollectionQueue(msg: DapexMessage): F[Unit] =
    logMessage("Collection Point", msg)

  private def logMessage(queue: String, msg: DapexMessage) =
    Logger[F].info(
      s"Received message: [Queue $queue], [Endpoint: ${msg.endpoint}], [Method: ${msg.endpoint.method}], [ID: ${msg.client.requestId}]"
    )
}

object RMQConsumer {

  def apply[F[_]: Logger](): RMQConsumer[F] = new RMQConsumer[F]()
}
