package models

import java.sql.Connection
import javax.inject.Inject

import anorm._
import anorm.SqlParser._
import play.api.db.DBApi
import play.api.libs.json.{Json, OWrites, Reads}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * Simplified representation of a todo item.
  * For now, it just consists of a title, completed status and a set of String comments.
  * Future extensions might include:
  * 1. Created date
  * 2. Modified date
  * 3. Due date (timed tasks)
  * 4. Metadata on comments such as above
  * 5. Body for a long description of the item
  *
  * @param id None for a new item, else the existing ID
  * @param title the description
  * @param completed has the task been completed
  * @param comments list of comments that apply to the item
  */
case class ToDoItem(id: Option[Long] = None, title: String, completed:Boolean,comments:Seq[String]) {
  //Manual validation for increased flexibility over the inbuilt JSON validation in Play, not necessary
  //for such simple use cases but generally required as complexity grows
  def validate:ListBuffer[String] = {
    val errors:ListBuffer[String] = ListBuffer()
    //These match the DB restrictions to return nicer errors to the user
    if(title.length > 200) errors += "Title exceeds maximum length of 200"
    if(comments.lengthCompare(20) > 0) errors += "Maximum of 20 comments"//Hardcoded for now, in a real app it might be configurable
    if(comments.exists(_.length > 500)) errors += "Maximum comment size of 500 characters"
    errors
  }
}

object ToDoItem {
  implicit def toParameters: ToParameterList[ToDoItem] =
    Macro.toParameters[ToDoItem]

  implicit val readFormat: Reads[ToDoItem] = Json.reads[ToDoItem]
  implicit val writeFormat: OWrites[ToDoItem] = Json.writes[ToDoItem]
}

@javax.inject.Singleton
class ToDoItemRepository @Inject()(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db = dbapi.database("default")

  private[models] val parser = {
    get[Option[Long]]("todo_item.id") ~ str("todo_item.title") ~ bool("todo_item.completed") ~ array[String]("todo_item.comments") map {
      case id ~ name  ~ completed ~ comments => ToDoItem(id, name,completed, comments.toSeq)
    }
  }

  def findById(id: Long): Future[Option[ToDoItem]] = Future {
    db.withConnection { implicit connection =>
      findInternal(id)
    }
  }(ec)

  private def findInternal(id: Long)(implicit connection:Connection) = {
    SQL"select * from todo_item where id = $id".as(parser.singleOpt)
  }

  /**
    * Retrieves all todo items from the DB.
    * Unlikely to be suitable for production usage as no predicates are present.
    *
    * @return all items
    */
  def list: Future[List[ToDoItem]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from todo_item".as(parser.*)
    }
  }(ec)

  /**
    * Updates an individual item.
    * @param id the id of the item
    * @param item the new contents of the item
    * @return echoes the input
    */
  def update(id: Long, item: ToDoItem):Future[Int] = Future {
    if(item.validate.isEmpty) {
      db.withTransaction { implicit connection =>
        updateInternal(id, item)
      }
    } else throw new IllegalArgumentException(item.validate.mkString(","))
  }(ec)

  private def updateInternal(id: Long, item: ToDoItem)(implicit connection: Connection) = {
    SQL(
      """
        update todo_item set title = {title}, completed = {completed},
          comments = {comments}
        where id = {id}
      """).bind(item.copy(id = Some(id))).on('comments->item.comments.toArray).executeUpdate()
  }

  /**
    * Inserts a new item.
    * @param item the contents of the item
    * @return echoes the input
    */
  def insert(item: ToDoItem): Future[ToDoItem] = Future {
    if(item.validate.isEmpty) {
      val assignedId: Option[Long] = db.withTransaction { implicit connection =>
        SQL(
          """
        insert into todo_item(title,completed,comments) values (
          {title}, {completed}, {comments}
        )
      """).bind(item).on('comments -> item.comments.toArray).executeInsert()
      }
      item.copy(id = assignedId)
    } else throw new IllegalArgumentException(item.validate.mkString(","))
  }(ec)

  def delete(id: Long):Future[Int] = Future {
    db.withConnection { implicit connection =>
      SQL"delete from todo_item where todo_item.id = $id".executeUpdate()
    }
  }(ec)

  /**
    * Allows updating of a resource but reusing the DB connection for both operations.
    * @param id to lookup the existing item with
    * @param block block to map from the existing item to the updated item
    * @return the updated item, if it was found
    */
  def updateWith(id:Long)(block:ToDoItem=>ToDoItem):Future[Option[ToDoItem]] = Future {
    db.withTransaction { implicit connection =>
      val maybeItem = findInternal(id)
      maybeItem.foreach(i => {
        val updated = block(i)
        if(updated.validate.isEmpty) {
          updateInternal(id, updated)
        }
        else throw new IllegalArgumentException(updated.validate.mkString(","))
      })
      maybeItem.map(block)
    }
  }
}
