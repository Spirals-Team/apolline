package controllers

import com.wordnik.swagger.annotations._
import models._
import play.api.i18n.Messages
import play.api.libs.json.{Json, JsObject}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import scala.concurrent._

import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * This class represent all information get when the user submit a form for insert or update a cards
 * @param modele Name of the modele type card
 * @param types Name of the type card
 * @param send Value on the button used for send the form
 */
case class TypeCardsForm(modele:String,types:String,send:Option[String]=None)

/**
 * This trait is a controller for manage sensors type
 */
trait TypeCardsManagerLike extends Controller {

  /** *********** Property *********************/

  /**
   * DAO for card type
   */
  val typeCardsDao: TypeCardsDao = TypeCardsDaoObj

  /**
   * DAO for cards
   */
  val cardDao: CardsDao = CardsDaoObj

  /**
   * Value contains the configuration of the form
   */
  lazy val form=Form[TypeCardsForm](
    mapping(
      "modele"->nonEmptyText,
      "types"->nonEmptyText,
      "send"->optional(text)
    )(TypeCardsForm.apply)(TypeCardsForm.unapply)
  )

  /** **************** Route methods ***********/

  /**
   * This method is call when the user is on the page /inventary/cards. It list cards type available
   * @return Return Ok Action when the user is on the page /inventary/cards with the list of cards type
   *         Return Redirect Action when the user is not log in
   *         Return Internal Server Error Action when have mongoDB error
   */
  @ApiOperation(
    nickname = "inventary",
    value = "Get the html page for list cards type",
    notes = "Get the html page for list cards type",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 303, message = "Move resource to the login page at /login if the user is not log"),
    new ApiResponse(code = 500, message = "Have a mongoDB error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(value = "Cards type name for filter all cards type", name = "sort", dataType = "String", paramType = "query")
  ))
  def inventary(sort: String = "",filtreSto:String = "") = Action.async {
    implicit request =>
      //Verify if user is connect
      UserManager.doIfconnectAsync(request) {
        //Create selector for select card type
        getInventaryTypeCards(Json.obj("delete"->false),sort){
          (listType,filtre,countCards)=>
            //Print the list of card type
            Ok(views.html.cards.listTypeCards(filtreSto,sort,filtreStock(filtreSto),listType,countCards,filtre))
        }
      }
  }

  /**
   * This method is call when the user is on the page /inventary/cards/type. It display a form for add new cards type
   * @return Return Ok Action when the user is on the page /inventary/cards/type with the form
   *         Return Redirect Action when the user is not log in
   */
  @ApiOperation(
    nickname = "inventary/cards",
    value = "Get the html page for insert a new cards type",
    notes = "Get the html page for insert a new cards type",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code=303,message="Move resource to the login page at /login if the user is not log")
  ))
  def typePage=Action.async{
    implicit request =>
      //Verify if user is connect
      UserManager.doIfconnectAsync(request) {
          //Display the form for insert new card type
          printForm(Results.Ok,form,routes.TypeCardsManager.typeInsert())
      }
  }

  /**
   * This method is call when the user is on the page /inventary/cards/:id/update. It display a form for update card type
   * @param id Cards type id
   * @return Return Ok Action when the user is on the page /inventary/cards/:id/update with the form
   *         Return Redirect Action when the user is not log in or card type information not found
   *         Return Internal Server Error Action when have mongoDB error
   */
  @ApiOperation(
    nickname = "inventary/cards/:id/update",
    value = "Get the html page for update card type",
    notes = "Get the html page for update card type",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code=303,message="<ul><li>Move resource to the login page at /login if the user is not log</li><li>Move resource to the card inventary page at /inventary/cards when card type not found"),
    new ApiResponse(code=500,message="Have a mongoDB error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(value = "Id of the card type",required=true,name="id", dataType = "String", paramType = "path")
  ))
  def typeUpdatePage(id:String)=Action.async{
    implicit request =>
      //Verify if user is connect
      UserManager.doIfconnectAsync(request) {

        //Find the card type
        typeCardsDao.findById(BSONObjectID(id)).flatMap(
          typeCardsOpt=> typeCardsOpt match{

            //Cards type not found redirect to the card inventary
            case None=>future{Redirect(routes.TypeCardsManager.inventary())}

            //Cards type found
            case Some(typeCards)=>{

              //Prepare data for prefilled the form
              val typeCardsData = TypeCardsForm(typeCards.modele,typeCards.types)

              //Display the form for update card type
              printForm(Results.Ok,form.fill(typeCardsData),routes.TypeCardsManager.typeUpdate(id))
            }
          }
        ).recover({
          //Send an Internal Server Error for mongoDB error
          case e=>InternalServerError("error")
        })
      }
  }

  /**
   * This method is call when the user submit a form for insert a new card type
   * @return Return Bad Request Action if the form was submit with data error
   *         Return Redirect Action when the user is not log in or card type is insert
   *         Return Internal Server Error Action when have mongoDB error
   */
  @ApiOperation(
    nickname = "inventary/cards/type",
    value = "Insert a new card type",
    notes = "Insert a new card type to the mongoDB database",
    httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code=303,message="<ul><li>Move resource to the login page at /login if the user is not log</li><li>Move resource to the card inventary page at /inventary/cards when card type is insert"),
    new ApiResponse(code=400,message="Fields required or not valid"),
    new ApiResponse(code=500,message="Have a mongoDB error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam (value = "Name of the card model",required=true,name="modele", dataType = "String", paramType = "form"),
    new ApiImplicitParam (value = "Name of the card type",required=true,name="types", dataType = "String", paramType = "form")
  ))
  def typeInsert=Action.async{
    implicit request=>
      val msg=Messages("inventary.typeCards.error.typeExist")+" <input type=\"submit\" class=\"btn btn-danger\" name=\"send\" value=\""+Messages("global.reactiver")+"\"/> <input type=\"submit\" class=\"btn btn-danger\" name=\"send\" value=\""+Messages("global.ignorer")+"\"/>"
      //Verify if the user is connect and if data received are valid
      submitForm(msg,routes.TypeCardsManager.typeInsert()) {
        typeData => Json.obj("modele" -> typeData.modele, "types" -> typeData.types)
      }{typeData=>{
        if(typeData.send.isEmpty || typeData.send.equals(Some("Ignorer"))) {
          //Insert card type
          typeCardsDao.insert(TypeCards(
            types = typeData.types,
            modele = typeData.modele
          )).map(
              //Redirect to the inventary if sensor type was insert
              e => Redirect(routes.TypeCardsManager.inventary())
            ).recover({
            //Send Internal Server Error if have mongoDB error
            case e => InternalServerError("error")
          })
        }
        else{
          updateWithColumnDelete(Json.obj("modele" -> typeData.modele, "types" -> typeData.types),false)
        }
      }
      }
  }

  /**
   * This method is call when the user submit a form for update a card type
   * @param id Sensor type id
   * @return Return Bad Request Action if the form was submit with data error
   *         Return Redirect Action when the user is not log in or card type is update
   *         Return Internal Server Error Action when have mongoDB error
   */
  @ApiOperation(
    nickname = "inventary/cards/:id/update",
    value = "Update a card type",
    notes = "Update a card type to the mongoDB database",
    httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code=303,message="<ul><li>Move resource to the login page at /login if the user is not log</li><li>Move resource to the card inventary page at /inventary/cards when card type is update"),
    new ApiResponse(code=400,message="Fields required or not valid"),
    new ApiResponse(code=500,message="Have a mongoDB error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(value = "Id of the card type",required=true,name="id", dataType = "String", paramType = "path"),
    new ApiImplicitParam (value = "Name of the card model",required=true,name="modele", dataType = "String", paramType = "form"),
    new ApiImplicitParam (value = "Name of the card type",required=true,name="types", dataType = "String", paramType = "form")
  ))
  def typeUpdate(id:String)=Action.async{
    implicit request=>
      val msg=Messages("inventary.typeCards.error.typeExist")+" <input type=\"submit\" class=\"btn btn-danger\" value=\""+Messages("global.ignorer")+"\"/>"
      //Verify if the user is connect and if data received are valid
      submitForm(msg,routes.TypeCardsManager.typeUpdate(id)){
        typeData => Json.obj("_id"->Json.obj("$ne"->BSONFormats.BSONObjectIDFormat.writes(BSONObjectID(id))),"modele" -> typeData.modele, "types" -> typeData.types)
      }{typeData=>{
        if(typeData.send.isEmpty || typeData.send.equals(Some("Ignorer"))) {
          //Update card type
          typeCardsDao.updateById(BSONObjectID(id),
            TypeCards(
              _id = BSONObjectID(id),
              types = typeData.types,
              modele = typeData.modele
            )).map(
              //Redirect to the inventary if card type was update
              e => Redirect(routes.TypeCardsManager.inventary())
            ).recover({
            //Send Internal Server Error if have mongoDB error
            case e => InternalServerError("error")
          })
        }else{
          printForm(Results.BadRequest, form.withGlobalError(msg).fill(typeData), routes.TypeCardsManager.typeUpdate(id))
        }
      }
      }
  }

  /**
   * This method is call when the user delete a card type
   * @param id Cards type id
   * @return Return Redirect Action when the user is not log in or card type is delete
   *         Return Internal Server Error Action when have mongoDB error
   */
  @ApiOperation(
    nickname = "inventary/cards/:id/delete",
    value = "Delete a card type",
    notes = "Delete a card type to the mongoDB database",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code=303,message="<ul><li>Move resource to the login page at /login if the user is not log</li><li>Move resource to the card inventary page at /inventary/cards when card type is delete"),
    new ApiResponse(code=500,message="Have a mongoDB error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(value = "Id of the card type",required=true,name="id", dataType = "String", paramType = "path")
  ))
  def delete(id:String)=Action.async{
    implicit request=>
      //Verify if user is connect
      UserManager.doIfconnectAsync(request) {

        val idFormat=BSONFormats.BSONObjectIDFormat.writes(BSONObjectID(id))
        //find the card
        cardDao.findOne(Json.obj("delete"->false,"types"->idFormat)).flatMap(
          data => data match{

            //Cards not found
            case None => updateWithColumnDelete(Json.obj("_id"->idFormat),true)

            //Cards found redirect to the cards inventary
            case _ =>future{Redirect(routes.TypeCardsManager.inventary())}
          }
        ).recover({
          //Send Internal Server Error if have mongoDB error
          case e => InternalServerError("error")
        })
      }
  }

  /****************  Methods  ***********************/

  /**
   * List type cards get depending on the query
   * @param selector Query for get type cards
   * @param filtreType Name of a particular type found
   * @param f Function for print the list of type cards
   * @return
   */
  def getInventaryTypeCards(selector: JsObject,filtreType:String)(f:(List[TypeCards],List[BSONDocument],List[BSONDocument])=>Result)={
    val selectorAll=if(filtreType.isEmpty){selector}else{selector ++ Json.obj("types"->filtreType)}

    //Find all type cards name
    val futureListType=typeCardsDao.findListType(BSONFormats.toBSON(selector).get.asInstanceOf[BSONDocument])
    //Find number of cards
    val futureCountCards=cardDao.countCards()

    //Find all card type
    typeCardsDao.findAll(selectorAll).flatMap(listType=>
      futureListType.flatMap(filtre=>
        futureCountCards.map(countCards=>

          //Print the list of type cards
          f(listType,filtre.toList,countCards.toList)

        ).recover({case _=>InternalServerError("error")})
      ).recover({case _=>InternalServerError("error")})
    ).recover({case _=>InternalServerError("error")})
  }

  /**
   * This method print a form with datalist
   * @param status Status of the response
   * @param form Information contains in the form
   * @param r Route used when submit a form
   * @param request Request received
   * @return
   */
  def printForm(status: Results.Status,form:Form[TypeCardsForm],r:Call)(implicit request:Request[AnyContent]):Future[Result]={
    //Find modele
    val futureModele=typeCardsDao.findListModele()

    //Find type
    val futureType=typeCardsDao.findListType(BSONDocument("delete"->false))

    futureModele.flatMap(modele=>
      futureType.map(types=>

        //Print the form
        status(views.html.cards.formType(form,modele.toList,types.toList,r))

      ).recover({case _=>InternalServerError("error")})
    ).recover({case _=>InternalServerError("error")})
  }

  /**
   * This method update just the delete column of a card type
   * @param selector JsObject for select the card type
   * @param delete Value of the delete column
   * @return
   */
  def updateWithColumnDelete(selector:JsObject,delete:Boolean)={
    //Find the card type
    typeCardsDao.findOne(selector).flatMap(
      cardTypeData=>cardTypeData match{
        case Some(typeCardsData)=>{

          //Update the card type and set the delete column to true
          typeCardsDao.updateById(
            typeCardsData._id,
            typeCardsData.copy(delete=delete)
          ).map(
              //Redirect to the cards inventary after delete cards type
              e => Redirect(routes.TypeCardsManager.inventary())
            ).recover({
            //Send Internal Server Error if have mongoDB error
            case e => InternalServerError("error")
          })
        }
        case _ => future{Redirect(routes.TypeCardsManager.inventary())}
      }
    ).recover({
      //Send Internal Server Error if have mongoDB error
      case e => InternalServerError("error")
    })
  }

  /**
   * Verify if the user is connect and if data received are valid then apply function dedicated
   * @param r Route use for submit the form
   * @param f Function dedicated
   * @param request
   * @return Return Bad request Action if the form is not valid
   *         Return Redirect if dedicated function is a success
   *         Return Internal server error if have mongoDB error
   */
  def submitForm(errorMessage:String,r:Call)(verif:TypeCardsForm=>JsObject)(f:TypeCardsForm=>Future[Result])(implicit request: Request[AnyContent]):Future[Result]={
    //Verify if user is connect
    UserManager.doIfconnectAsync(request) {
      form.bindFromRequest.fold(

        //If form contains errors
        formWithErrors => {
            //the form is redisplay with error descriptions
            printForm(Results.BadRequest, formWithErrors, r)
        },

        // Else if form no contains errors
        typeData => {

          //Find the sensor type
          typeCardsDao.findAll(verif(typeData)).flatMap(
            data =>{
              //If sensor type not found
              if(data.size==0 || List(Some("Réactiver"),Some("Ignorer")).contains(typeData.send)) {
                f(typeData)
              }
              else if(data.filter(p => !(p.delete) ).size>0) {
                //print form with prefilled data and a bad request
                printForm(Results.BadRequest, form.withGlobalError(Messages("inventary.typeCards.error.typeExist")).fill(typeData), r)
              }
              else {
                printForm(Results.BadRequest, form.withGlobalError(errorMessage).fill(typeData), r)
              }
            }
          ).recover({
            //Send Internal Server Error if have mongoDB error
            case e => InternalServerError("error")
          })
        }
      )
    }
  }

  /**
   * Verify if card type found and execute a function if card type found or not
   * @param id Cards type id
   * @param found Function executed if card type found
   * @param notFound Function executed if card type not found
   * @return Return the result of executed function
   */
  def doIfTypeCardsFound(id:BSONObjectID)(found:Unit=>Future[Result])(notFound:Unit=>Future[Result]):Future[Result]={
    //Find the card type
    typeCardsDao.findOne(Json.obj("_id"->BSONFormats.BSONObjectIDFormat.writes(id),"delete"->false)).flatMap(
      typeCardsOpt => typeCardsOpt match{

        //If the card type not found execute function not found
        case None => notFound()

        //If the card type found execute function found
        case Some(_) => found()
      }
    ).recover({
      //Send Internal Server Error if have mongoDB error
      case _=> InternalServerError("error")
    })
  }

  def filtreStock(filtre:String)(v:Int)=filtre match{
    case "yes" => v>0
    case "no" => v==0
    case _ => v>=0
  }
}

@Api(value = "/typeCards", description = "Operations for cards type")
object TypeCardsManager extends TypeCardsManagerLike
