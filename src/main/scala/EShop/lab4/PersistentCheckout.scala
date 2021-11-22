package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab5.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command], message: Command): Cancellable =
    context.scheduleOnce(timerDuration, context.self, message)

  def apply(cartActor: ActorRef[TypedCartActor.Event], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Event]
  ): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case WaitingForStart =>
          command match {
            case StartCheckout =>
              Effect.persist(CheckoutStarted)
            case _ =>
              Effect.unhandled
          }

        case SelectingDelivery(_) =>
          command match {
            case SelectDeliveryMethod(method) =>
              Effect.persist(DeliveryMethodSelected(method))

            case ExpireCheckout | CancelCheckout =>
              Effect.persist(CheckoutCancelled)

            case _ =>
              Effect.unhandled
          }

        case SelectingPaymentMethod(_) =>
          command match {
            case SelectPayment(payment, orderManagerAdapter) =>
              val paymentActor =
                context.spawn(Payment(payment, orderManagerAdapter, context.self), "paymentActor")

              Effect
                .persist(PaymentStarted(paymentActor))
                .thenReply(orderManagerAdapter)(_ => PaymentStarted(paymentActor))

            case ExpireCheckout | CancelCheckout =>
              Effect.persist(CheckoutCancelled)

            case _ =>
              Effect.unhandled
          }

        case ProcessingPayment(_) =>
          command match {
            case ExpirePayment | CancelCheckout =>
              Effect.persist(CheckoutCancelled)

            case ConfirmPaymentReceived =>
              Effect.persist(CheckOutClosed).thenReply(cartActor)(_ => TypedCartActor.CheckoutClosed)

            case _ =>
              Effect.unhandled
          }

        case Cancelled =>
          Effect.none

        case Closed =>
          Effect.none
      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      state.timerOpt.foreach(_.cancel())
      event match {
        case CheckoutStarted           => SelectingDelivery(schedule(context, ExpireCheckout))
        case DeliveryMethodSelected(_) => SelectingPaymentMethod(schedule(context, ExpireCheckout))
        case PaymentStarted(_)         => ProcessingPayment(schedule(context, ExpirePayment))
        case CheckOutClosed            => Closed
        case CheckoutCancelled         => Cancelled
      }
    }
}
