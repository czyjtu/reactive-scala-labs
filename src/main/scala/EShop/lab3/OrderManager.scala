package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import EShop.lab2.CartActor

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
  case object PaymentRejected                                                                         extends Command
  case object PaymentRestarted                                                                        extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          val cartActor = context.spawn(TypedCartActor(), s"cart")
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cartActor)
        case _ =>
          Behaviors.same
      }
    )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same
        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(context.self)
          inCheckout(cartActor, sender)
        case _ =>
          Behaviors.same
      }
    )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case ConfirmCheckoutStarted(checkoutActor) =>
          senderRef ! Done
          inCheckout(checkoutActor)
        case _ =>
          Behaviors.same
      }
    )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) => {
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
          inPayment(sender)
        case _ =>
          Behaviors.same
      }
    })

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) => {
      msg match {
        case ConfirmPaymentStarted(paymentRef) =>
          senderRef ! Done
          inPayment(paymentRef, senderRef)
        case _ =>
          Behaviors.same
      }
    })

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) => {
      msg match {
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          inPayment(paymentActorRef, sender)
        case ConfirmPaymentReceived =>
          senderRef ! Done
          finished
        case _ =>
          Behaviors.same
      }
    })

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
