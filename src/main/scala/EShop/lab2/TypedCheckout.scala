package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def checkoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def paymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case StartCheckout => selectingDelivery(checkoutTimer(context))
      case _             => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case CancelCheckout | ExpireCheckout =>
        cancelled
      case SelectDeliveryMethod(method) =>
        timer.cancel()
        selectingPaymentMethod(checkoutTimer(context))
      case _ => Behaviors.same
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case CancelCheckout | ExpireCheckout =>
        cancelled
      case SelectPayment(method, _) =>
        timer.cancel()
        processingPayment(paymentTimer(context))
      case _ =>
        Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case CancelCheckout | ExpirePayment => cancelled
    case ConfirmPaymentReceived         => closed
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
