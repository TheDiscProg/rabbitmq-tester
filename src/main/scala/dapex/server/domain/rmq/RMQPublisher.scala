package dapex.server.domain.rmq

import cats.effect.Temporal
import cats.syntax.all._
import dapex.messaging._
import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.publisher.DapexMQPublisher
import fs2._

import scala.concurrent.duration._

object RMQPublisher {

  def publish[F[_]: Temporal](
      publisher: DapexMQPublisher[F],
      queues: List[RabbitQueue]
  ): Stream[F, Int] = {
    val publishers: List[Stream[F, Int]] = queues.map(q => startPublishingToQueue(publisher, q))
    val empty: Stream[F, Nothing] = Stream.empty.covary[F]
    val folded = publishers.fold(empty)((s1, s2) => s1.merge(s2))
    folded
  }

  private def startPublishingToQueue[F[_]: Temporal](
      publisher: DapexMQPublisher[F],
      queue: RabbitQueue
  ): Stream[F, Int] = {
    val stream: Stream[F, Int] = Stream
      .iterateEval(1) { i =>
        val msg = queue match {
          case RabbitQueue.SERVICE_AUTHENTICATION_QUEUE =>
            request.copy(
              client = request.client.copy(requestId = s"app-req-$i")
            )
          case RabbitQueue.SERVICE_DBREAD_QUEUE =>
            request.copy(
              endpoint = request.endpoint.copy(resource = "service.dbread"),
              client = request.client
                .copy(clientId = "service.auth", requestId = s"service.auth-req-dbread-$i")
            )
          case RabbitQueue.SERVICE_COLLECTION_POINT_QUEUE =>
            request.copy(
              endpoint = request.endpoint.copy(resource = "service.collection-point"),
              client = request.client
                .copy(clientId = "service.auth", requestId = s"service.auth-req-cp-$i")
            )
        }
        publisher.publishMessageToQueue(msg, queue) *>
          (i + 1).pure[F]
      }
      .metered(1.seconds)
      .interruptAfter(50.seconds)
    stream
  }

  val request = DapexMessage(
    endpoint = Endpoint(resource = "service.auth", method = "select"),
    client = Client(
      clientId = "app-1",
      requestId = "app-req-1",
      sourceEndpoint = "service.auth",
      authorisation = ""
    ),
    originator = Originator(
      clientId = "app-1",
      requestId = "app-req-1",
      sourceEndpoint = "auth"
    ),
    criteria = Vector(
      Criterion("username", "test@test.com", "EQ"),
      Criterion("password", "password1234", "EQ")
    ),
    update = Vector(),
    insert = Vector(),
    process = Vector(),
    response = None
  )

}
