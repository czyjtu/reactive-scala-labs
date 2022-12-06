package EShop.lab5

import EShop.lab5.ProductCatalog.{GetItems, Item, Items, ProductCatalogServiceKey}
import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives.{as, complete, entity, onSuccess, path, post}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}


trait CatalogJsonSupport
  extends SprayJsonSupport
    with DefaultJsonProtocol {

  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue =
      JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _ => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat: RootJsonFormat[Item] = jsonFormat5(Item)
  implicit val returnFormat: RootJsonFormat[Items] = jsonFormat1(Items)
  implicit val queryFormat: RootJsonFormat[CatalogHttpServer.ProductsQuery] = jsonFormat2(CatalogHttpServer.ProductsQuery)

}

object CatalogHttpServer {
  case class ProductsQuery(brand: String, keywords: List[String])
  def apply(): CatalogHttpServer = new CatalogHttpServer
}

class CatalogHttpServer extends CatalogJsonSupport {
  implicit val system = ActorSystem[Nothing](Behaviors.empty, "ProductCatalog")
  implicit val timeout: Timeout = 1.second
  implicit val scheduler = system.scheduler
  implicit val executionContext = system.executionContext


  import CatalogHttpServer._

  def routes: Route = {
    path("products")(
      post {
        entity(as[ProductsQuery]) {
          query =>
            val listingFuture = system.receptionist.ask(
              (replyTo: ActorRef[Receptionist.Listing]) =>
                Receptionist.find(ProductCatalogServiceKey, replyTo))

            val itemsFuture = for {
              ProductCatalog.ProductCatalogServiceKey.Listing(listing) <- listingFuture
              productCatalog = listing.head
              items <- productCatalog.ask(ref => GetItems(query.brand, query.keywords, ref)).mapTo[ProductCatalog.Items]
            } yield items

            onSuccess(itemsFuture)(complete(_))
        }
      }
    )
  }

  def start(port: Int): Future[Done] = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object CatalogAppServer extends App {
  CatalogHttpServer().start(9000)
}
