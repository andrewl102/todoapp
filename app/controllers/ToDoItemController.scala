package controllers

import javax.inject.Inject

import models._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
class ToDoItemController @Inject()(
                                toDoItemRepository: ToDoItemRepository
                               ,val controllerComponents: ControllerComponents
                              )(implicit ec: ExecutionContext)
  extends BaseController {

  //In general responses will echo the item as a 200 JSON response, a 400 if it fails validation
  //and 404 if the item cannot be found
  implicit def optionToResult(f:Future[Option[ToDoItem]]):Future[Result] = {
    f.transform {
      case Success(s) if s.isDefined=> Success(Ok(Json.toJson(s.get)))
      case Success(_)=> Success(NotFound)
      case Failure(fail)=> fail match {
        case i:IllegalArgumentException => Success(BadRequest(i.getMessage))
        case t => Success(InternalServerError(t.getMessage))
      }
    }
  }

  def list: Action[AnyContent] = Action.async {
    toDoItemRepository.list.map(l=>Ok(Json.toJson(l)))
  }

  def find(id:Long) = Action.async {
    toDoItemRepository.findById(id)
  }

  //Here we return a 201 instead of a 200
  def insert = Action.async(parse.json) { implicit r=>
    withValidItem(r.body) { item=>
      toDoItemRepository.insert(item).
        map(i => Created(Json.toJson(i)).
          withHeaders(LOCATION->routes.ToDoItemController.find(i.id.get).absoluteURL()))
    }
  }

  def delete(id:Long) = Action.async {
    toDoItemRepository.delete(id).map(x => { //No rows indicate id could not be found
      if(x == 0) NotFound else Ok
    })
  }

  def update(id: Long) = Action.async(parse.json) { r =>
    withValidItem(r.body) { item: ToDoItem =>
      toDoItemRepository.update(id, item).map[Option[ToDoItem]] { x =>
        Some(x).filterNot(_ == 0).map(_ => item.copy(id = Some(id))) //200 if found,404 otherwise
      }
    }
  }

  //Here we explicitly validate before calling the DB too. This is currently redundant but it could be used
  //for different validation logic
  def withValidItem(i:JsValue)(block :(ToDoItem) => Future[Result]):Future[Result] = {
    val item = i.as[ToDoItem]
    val errors = item.validate
    if(errors.isEmpty) block(item) else Future.successful(BadRequest(errors.mkString(",")))
  }

  //Supplementary logic, the update/insert above covers all required functionality
  def complete(id: Long) = Action.async {
    toDoItemRepository.updateWith(id)(i=>i.copy(completed = true))
  }

  def uncomplete(id: Long) = Action.async {
    toDoItemRepository.updateWith(id)(i=>i.copy(completed = false))
  }

  def addComment(id:Long) = Action.async(parse.json) { implicit r=>
    val comment = r.body.as[String]
    toDoItemRepository.updateWith(id)(i=>i.copy(comments = i.comments :+ comment))
  }
}
