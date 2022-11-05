package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  def apply(cartActor: ActorRef[TypedCartActor.Command]) = new TypedCheckout(cartActor).start

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
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 4 seconds
  val paymentTimerDuration: FiniteDuration  = 4 seconds

  private def checkoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def paymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case StartCheckout => selectingDelivery(checkoutTimer(context))
        case _             => Behaviors.same
      }
    )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case CancelCheckout | ExpireCheckout =>
          cancelled
        case SelectDeliveryMethod(method) =>
          timer.cancel()
          selectingPaymentMethod(checkoutTimer(context))
        case _ => Behaviors.same
      }
    )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case CancelCheckout | ExpireCheckout =>
          cancelled
        case SelectPayment(method, orderActor) =>
          timer.cancel()
          val paymentActor = context.spawn(Payment(method, orderActor, context.self), s"payment")
          orderActor ! OrderManager.ConfirmPaymentStarted(paymentActor)
          processingPayment(paymentTimer(context))
        case _ =>
          Behaviors.same
      }
    )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {
      case CancelCheckout | ExpirePayment => cancelled
      case ConfirmPaymentReceived =>
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        closed
      case _ =>
        Behaviors.same
    }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
