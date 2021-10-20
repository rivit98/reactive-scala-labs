package EShop.lab2

import EShop.lab2.CartActor.{ConfirmCheckoutCancelled, ConfirmCheckoutClosed}
import EShop.lab2.Checkout.{CancelCheckout, ConfirmPaymentReceived, SelectDeliveryMethod, SelectPayment}
import akka.actor.{ActorSystem, Props}

import scala.io.StdIn.readLine

object Main extends App {
  val system = ActorSystem("Eshop")
  val cart   = system.actorOf(Props[CartActor], "cartActor")

  while (true) {
    println("""> a item1 [item2] - add items to cart
        |> r item1 [item2] - remove items from cart
        |> c - checkout
        |""".stripMargin)

    val input  = readLine
    val tokens = input.split(" ")
    val cmd    = tokens.head
    val items  = tokens.drop(1)
    if (cmd == "a") {
      items.foreach(i => {
        cart ! CartActor.AddItem(i)
      })
    } else if (cmd == "r") {
      items.foreach(i => {
        cart ! CartActor.RemoveItem(i)
      })
    } else if (cmd == "c") {
      val checkout = system.actorOf(Props[Checkout], "checkoutActor")
      cart ! CartActor.StartCheckout
      checkout ! Checkout.StartCheckout
      print("Enter delivery method (q - for cancel): ")

      val delivery = readLine
      if (delivery == "q") {
        cart ! ConfirmCheckoutCancelled
        checkout ! CancelCheckout
      } else {
        checkout ! SelectDeliveryMethod(delivery)
        print("Enter payment method (q - for cancel): ")
        val payment = readLine

        if (payment == "q") {
          cart ! ConfirmCheckoutCancelled
          checkout ! CancelCheckout
        } else {
          checkout ! SelectPayment(payment)
          print("Confirm payment (y/n): ")
          val confirmation = readLine
          if (confirmation == "n") {
            cart ! ConfirmCheckoutCancelled
            checkout ! CancelCheckout
          } else {
            cart ! ConfirmCheckoutClosed
            checkout ! ConfirmPaymentReceived
          }
        }
      }
    }
  }
}
