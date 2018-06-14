
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.db.{DBApi, Database}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Try

class ModelSpec extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with BeforeAndAfter{
  import models._
  import scala.concurrent.ExecutionContext.Implicits.global
  // --
  def toDoRepo: ToDoItemRepository = app.injector.instanceOf(classOf[ToDoItemRepository])
  val dbApi: DBApi = app.injector.instanceOf(classOf[DBApi])
  val db: Database = dbApi.database("default")
  def longString: String = (for (_ <- 1 to 201) yield "a").mkString
  def tooLongForCommentString: String = (for (_ <- 1 to 501) yield "a").mkString
  def baseItem: ToDoItem = ToDoItem(None,"Hi",completed = false,Array("1","2"))

  "Todo items" should {

    "be inserted and retrieved correctly" in {
      whenReady(toDoRepo.insert(baseItem),timeout(2 seconds)) { inserted =>
        //Check using find to make sure it was correctly inserted
        whenReady(toDoRepo.findById(inserted.id.get)) { found =>
          found.isDefined mustBe true
          found.get mustEqual baseItem.copy(id = found.get.id)
        }
      }
    }

    "fail validation when exceeding limits for title" in {
      val attempt = Await.ready(toDoRepo.insert(baseItem.copy(title = longString)),2 seconds).value.get
      assertTitleFailure(attempt)
    }

    "fail validation when exceeding limits for all requirements" in {
      val comments = for(_ <- 1 to 21) yield tooLongForCommentString
      val attempt = Await.ready(toDoRepo.insert(baseItem.copy(comments = comments)),2 seconds).value.get
      attempt.isFailure.mustEqual(true)
      attempt.failed.get.getMessage mustEqual "Maximum of 20 comments,Maximum comment size of 500 characters"
    }

    "fail validation when exceeding limits for title in update" in {
      whenReady(toDoRepo.insert(baseItem),timeout(2 seconds)) { inserted =>
        val attempt = Await.ready(toDoRepo.update(inserted.id.get, inserted.copy(title = longString)),2 seconds).value.get
        assertTitleFailure(attempt)
      }
    }

    "fail validation when exceeding limits for title in updateWith" in {
      whenReady(toDoRepo.insert(baseItem),timeout(2 seconds)) { inserted =>
        val attempt = Await.ready(toDoRepo.updateWith(inserted.id.get)(i => i.copy(title = longString)),2 seconds).value.get
        assertTitleFailure(attempt)
      }
    }

    "Update correctly" in {
      whenReady(toDoRepo.insert(baseItem),timeout(2 seconds)) { inserted =>
        Await.result(toDoRepo.update(inserted.id.get, inserted.copy(title = "Updated")),2 seconds)
        Await.result(toDoRepo.findById(inserted.id.get),2 seconds).get.title mustBe "Updated"
      }
    }

    "Update correctly using updateWith" in {
      whenReady(toDoRepo.insert(baseItem),timeout(2 seconds)) { inserted =>
        Await.result(toDoRepo.updateWith(inserted.id.get)(i=>i.copy(title="Updated2")),2 seconds)
        Await.result(toDoRepo.findById(inserted.id.get),2 seconds).get.title mustBe "Updated2"
      }
    }

    "be listed correctly by" in {
      val toInsert = ToDoItem(None, "Hi", completed = false, Array("1", "2"))
      whenReady(Future.sequence(Seq(toDoRepo.insert(toInsert), toDoRepo.insert(toInsert.copy(title = "Hi2"))))) { _ =>
        whenReady(toDoRepo.list) { items =>
          items.length mustBe 2
          items.exists(_.title == "Hi") mustBe true
          items.exists(_.title == "Hi2") mustBe true
        }
      }
    }
  }

  private def assertTitleFailure(attempt: Try[_]) = {
    attempt.isFailure.mustEqual(true)
    attempt.failed.get.getMessage mustEqual "Title exceeds maximum length of 200"
  }

  before {
    db.withTransaction { c=>
      c.prepareStatement("DELETE FROM todo_item").executeUpdate()
    }
  }
}
