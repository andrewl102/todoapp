
import akka.stream.Materializer
import controllers.ToDoItemController
import models.{ToDoItem, ToDoItemRepository}
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.db.{DBApi, Database}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Result}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

//noinspection Specs2Matchers
class FunctionalSpec extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with BeforeAndAfter {
  def toDoRepo: ToDoItemRepository = app.injector.instanceOf(classOf[ToDoItemRepository])
  val dbApi: DBApi = app.injector.instanceOf(classOf[DBApi])
  val db: Database = dbApi.database("default")
  implicit lazy val materializer: Materializer = app.materializer

  def controller: ToDoItemController = app.injector.instanceOf(classOf[ToDoItemController])

  "ToDoItemController" should {

    "List items" in {
      //Setup
      Await.result(toDoRepo.insert(ToDoItem(None,"title1",completed = false,Nil)),2 seconds)
      val result = controller.list()(FakeRequest())
      status(result) must equal(OK)
      val items = contentAsJson(result).as[List[ToDoItem]]
      items.length must equal(1)
      items.head.title mustEqual "title1"
    }

    "Get by id when item exists" in {
      //Setup
      val created = Await.result(toDoRepo.insert(ToDoItem(None,"title1",completed = false,Nil)),2 seconds)
      val result = controller.find(created.id.get)(FakeRequest())
      status(result) must equal(OK)
      val items = contentAsJson(result).as[ToDoItem]
      items.title mustEqual "title1"
    }

    "404 when id does not exist" in {
      //Setup
      val result = controller.find(-1)(FakeRequest())
      status(result) must equal(NOT_FOUND)
    }

    "404 when id does not exist for delete" in {
      //Setup
      val result = controller.delete(-1)(FakeRequest())
      status(result) must equal(NOT_FOUND)
    }

    "Delete items" in {
      val created = Await.result(toDoRepo.insert(ToDoItem(None,"title1",completed = false,Nil)),2 seconds)
      val result = controller.delete(created.id.get)(FakeRequest())
      status(result) must equal(OK)
      val result2 = controller.list()(FakeRequest())
      val items = contentAsJson(result2).as[List[ToDoItem]]
      items.isEmpty must equal(true)
    }

    "Insert and update correctly" in {
      val item = ToDoItem(None,"newTitle",completed = false,List("comment1"))
      val value = FakeRequest().withBody(Json.toJson(item))
      val acceptAction: Action[JsValue] = controller.insert
      val response: Future[Result] = acceptAction.apply(value) // <-- different type here
      status(response) must equal (CREATED)
      val parsed = contentAsJson(response).as[ToDoItem]
      headers(response).get("Location") mustEqual Some(s"http://localhost/item/${parsed.id.get}")

      val updated = parsed.copy(title = "Updated title")
      val response2 = controller.update(parsed.id.get)(FakeRequest().withBody(Json.toJson(updated)))
      status(response2) mustEqual OK

      //Check it really updated
      val result3 = controller.find(updated.id.get)(FakeRequest())
      status(result3) must equal(OK)
      val retrieved = contentAsJson(result3).as[ToDoItem]
      retrieved.title mustEqual "Updated title"
    }

    "Fail validation for bad JSON" in {
      val result = controller.insert(FakeRequest().withTextBody("{").withHeaders("Content-Type"->"application/json").withMethod("POST"))
      status(result) mustEqual BAD_REQUEST
    }

    "Complete and uncomplete" in {
      val created = Await.result(toDoRepo.insert(ToDoItem(None,"title1",completed = false,Nil)),2 seconds)
      val result = controller.complete(created.id.get)(FakeRequest())
      status(result) must equal(OK)
      contentAsJson(controller.find(created.id.get)(FakeRequest())).as[ToDoItem].completed mustEqual true
      val result2 = controller.uncomplete(created.id.get)(FakeRequest())
      status(result2) must equal(OK)
      contentAsJson(controller.find(created.id.get)(FakeRequest())).as[ToDoItem].completed mustEqual false
    }

    "Add comments" in {
      val created = Await.result(toDoRepo.insert(ToDoItem(None,"title1",completed = false,Nil)),2 seconds)
      val result = controller.addComment(created.id.get)(FakeRequest().withBody(Json.toJson("MyComment")))
      status(result) must equal(OK)
      contentAsJson(controller.find(created.id.get)(FakeRequest())).as[ToDoItem].comments.head mustEqual "MyComment"
    }

  }

  before {
    db.withTransaction { c=>
      c.prepareStatement("DELETE FROM todo_item").executeUpdate()
    }
  }
}
