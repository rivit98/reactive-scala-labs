package EShop.lab3

import EShop.lab2.TypedCartActor.CheckoutStarted
import EShop.lab2.TypedCheckout.PaymentStarted
import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.Payment.PaymentReceived
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] =
    Behaviors.setup { context =>
      val cartActor = context.spawn(new TypedCartActor().start, "cartActor")
      open(cartActor)
    }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cartActor)

        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          open(cartActor)

        case Buy(sender) =>
          val cartEventAdapter: ActorRef[TypedCartActor.Event] =
            context.messageAdapter {
              case CheckoutStarted(checkoutRef) => ConfirmCheckoutStarted(checkoutRef)
            }
          cartActor ! TypedCartActor.StartCheckout(cartEventAdapter)
          sender ! Done
          inCheckout(cartActor, sender)

        case _ =>
          Behaviors.stopped
      }
    }

  def inCheckout(
                  cartActorRef: ActorRef[TypedCartActor.Command],
                  senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.receiveMessage {
        case ConfirmCheckoutStarted(checkoutRef) =>
          buffer.unstashAll(inCheckout(checkoutRef))

        case msg =>
          buffer.stash(msg)
          Behaviors.same
      }
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, msg) =>
      val checkoutEventAdapter: ActorRef[Any] =
        context.messageAdapter {
          case PaymentStarted(paymentRef) => ConfirmPaymentStarted(paymentRef)
          case PaymentReceived => ConfirmPaymentReceived
        }

      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, checkoutEventAdapter)
          sender ! Done
          inPayment(sender)

        case _ =>
          Behaviors.stopped
      }
    }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.receiveMessage {
        case ConfirmPaymentStarted(paymentRef) =>
          paymentRef ! Payment.DoPayment
          buffer.unstashAll(inPayment(paymentRef, senderRef))

        case msg =>
          buffer.stash(msg)
          Behaviors.same
      }
    }

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receiveMessage {
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        sender ! Done
        inPayment(paymentActorRef, sender)

      case ConfirmPaymentReceived =>
        finished

      case _ =>
        Behaviors.stopped
    }

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
