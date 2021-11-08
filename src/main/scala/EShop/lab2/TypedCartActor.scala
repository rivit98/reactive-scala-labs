package EShop.lab2

import EShop.lab2.TypedCheckout.CheckOutClosed
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerAdapter: ActorRef[TypedCartActor.Event]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive { (context, msg) =>
    {
      msg match {
        case AddItem(item) =>
          nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))

        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same

        case _ =>
          Behaviors.stopped
      }
    }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive { (context, msg) =>
    {
      msg match {
        case AddItem(item) =>
          timer.cancel()
          nonEmpty(cart.addItem(item), scheduleTimer(context))

        case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
          timer.cancel()
          empty

        case RemoveItem(item) if cart.contains(item) =>
          timer.cancel()
          nonEmpty(cart.removeItem(item), scheduleTimer(context))

        case ExpireCart =>
          timer.cancel()
          empty

        case StartCheckout(orderManagerAdapter) =>
          val checkoutEventAdapter: ActorRef[TypedCheckout.Event] =
            context.messageAdapter {
              case CheckOutClosed => ConfirmCheckoutClosed
            }

          timer.cancel()
          val checkoutActor = context.spawn(new TypedCheckout(checkoutEventAdapter).start, "checkoutActor")
          checkoutActor ! TypedCheckout.StartCheckout
          orderManagerAdapter ! CheckoutStarted(checkoutActor)

          inCheckout(cart)

        case GetItems(sender) =>
          sender ! cart
          Behaviors.same

        case _ =>
          Behaviors.stopped
      }
    }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive { (context, msg) =>
    {
      msg match {
        case ConfirmCheckoutClosed =>
          empty

        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))

        case _ =>
          Behaviors.stopped
      }
    }
  }
}
