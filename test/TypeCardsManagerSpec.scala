
import java.util.Date

import controllers.{TypeCardsForm, TypeCardsManager, TypeCardsManagerLike}
import models.{Cards, CardsDao, TypeCards, TypeCardsDao}
import org.junit.runner.RunWith
import org.specs2.matcher.{MatchResult, Expectable, Matcher}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{Writes, Json, JsObject}
import play.api.mvc.{Result, Action, Results, Call}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, WithApplication}
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import reactivemongo.core.commands.{GetLastError, LastError}
import scala.concurrent._

@RunWith(classOf[JUnitRunner])
class TypeCardsManagerSpec extends Specification with Mockito {

  class TypeCardsManagerTest extends TypeCardsManagerLike

  case class matchRegex(a: String) extends Matcher[String]() {
    override def apply[S <: String](t: Expectable[S]): MatchResult[S] = result(a.r.findFirstIn(t.value).nonEmpty, "okMessage", "not found " + a, t)
  }

  case class contains(a: String, b: Int) extends Matcher[String]() {
    override def apply[S <: String](t: Expectable[S]): MatchResult[S] = result(a.r.findAllMatchIn(t.value).size == b, "okMessage", "not found " + b + " times but " + a.r.findAllMatchIn(t.value).size + " times " + a, t)
  }

  val bson=BSONObjectID.generate
  val bson2=BSONObjectID.generate

  def fixture = new {
    val typeCardsDaoMock = mock[TypeCardsDao]
    val cardDaoMock = mock[CardsDao]
    val controller = new TypeCardsManagerTest {
      override val typeCardsDao: TypeCardsDao = typeCardsDaoMock
      override val cardDao: CardsDao = cardDaoMock
    }
  }

  "When user is not connected, TypeCardsManager" should {
    "redirect to login for resource /inventary/cards" in new WithApplication {
      route(FakeRequest(GET, "/inventary/cards")).map(
        r=>{
          status(r) must equalTo(SEE_OTHER)
          header("Location",r) must equalTo(Some("/login"))
        }
      ).getOrElse(
        failure("Pas de retour de la fonction")
      )
    }

    "redirect to login for GET resource /inventary/cards/type" in new WithApplication{
      route(FakeRequest(GET, "/inventary/cards/type")).map(
        r=>{
          status(r) must equalTo(SEE_OTHER)
          header("Location",r) must equalTo(Some("/login"))
        }
      ).getOrElse(
          failure("Pas de retour de la fonction")
        )
    }

    "redirect to login for POST resource /inventary/cards/type" in new WithApplication{
      route(FakeRequest(POST, "/inventary/cards/type")).map(
        r=>{
          status(r) must equalTo(SEE_OTHER)
          header("Location",r) must equalTo(Some("/login"))
        }
      ).getOrElse(
          failure("Pas de retour de la fonction")
        )
    }

    "redirect to login for GET resource /inventary/cards/:id/update" in new WithApplication{
      route(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/update")).map(
        r=>{
          status(r) must equalTo(SEE_OTHER)
          header("Location",r) must equalTo(Some("/login"))
        }
      ).getOrElse(
          failure("Pas de retour de la fonction")
        )
    }

    "redirect to login for POST resource /inventary/cards/:id/update" in new WithApplication{
      route(FakeRequest(POST, "/inventary/cards/"+bson.stringify+"/update")).map(
        r=>{
          status(r) must equalTo(SEE_OTHER)
          header("Location",r) must equalTo(Some("/login"))
        }
      ).getOrElse(
          failure("Pas de retour de la fonction")
        )
    }
  }

  "When user is on the resource /inventary/cards , TypeCardsManager" should {
    "send 200 on OK with the message 'Aucun résultat trouvé'" in new WithApplication {
      val f=fixture

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(List())) returns future{List()}

      val r = f.controller.inventary().apply(FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}"""))
      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must contain("<h3 style=\"text-align:center\">Aucun résultat trouvé</h3>")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was one(f.cardDaoMock).countCards()
      there was one(f.cardDaoMock).countUsedCards(org.mockito.Matchers.eq(List()))
    }

    "send 200 on OK with 1 result" in new WithApplication {
      val f=fixture
      val typeCards=List[TypeCards](TypeCards(bson,"mod","type"))

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns future{typeCards}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument](BSONDocument("_id"->bson,"count"->5))}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(typeCards)) returns future{List((bson,3))}

      val r = f.controller.inventary().apply(FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}"""))
      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must not contain("<h3 style=\"text-align:center\">Aucun résultat trouvé</h3>")
      content must contain("type")
      content must contain("mod")
      content must matchRegex("<span class=\"bold\">\\s*Stock\\s*</span>\\s*:\\s*2 / 5")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was one(f.cardDaoMock).countCards()
      there was one(f.cardDaoMock).countUsedCards(org.mockito.Matchers.eq(typeCards))
    }

    "send 200 on OK with 2 results" in new WithApplication {
      val f=fixture
      val typeCards=List[TypeCards](
        TypeCards(bson,"mod","type"),
        TypeCards(bson2,"mod2","type2")
      )

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns future{typeCards}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument](BSONDocument("_id"->bson,"count"->5))}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(typeCards)) returns future{List((bson,3),(bson2,0))}

      val r = f.controller.inventary().apply(FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must not contain("<h3 style=\"text-align:center\">Aucun résultat trouvé</h3>")
      content must contain("type")
      content must contain("mod")
      content must matchRegex("<span class=\"bold\">\\s*Stock\\s*</span>\\s*:\\s*2 / 5")

      content must contain("type2")
      content must contain("mod2")
      content must matchRegex("<span class=\"bold\">\\s*Stock\\s*</span>\\s*:\\s*0 / 0")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was one(f.cardDaoMock).countCards()
      there was one(f.cardDaoMock).countUsedCards(org.mockito.Matchers.eq(typeCards))
    }
  }

  "When method getInventaryTypeCards is called, TypeCardsManager" should{

    "Call function for print result if method is called without filter" in new WithApplication {
      val f=fixture
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson)), any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson))) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(List())) returns future{List()}
      func.apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]]) returns Results.Ok("call function")

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj("_id"->bson),"","")(func)}
      val r=call(action,req)
      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("call function")

      there was one(f.typeCardsDaoMock).findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson)), any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson)))
      there was one(f.cardDaoMock).countCards()
      there was one(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "Call function for print result if method is called with type filter" in new WithApplication {
      val f=fixture
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"types"->"typ")), any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson))) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(List())) returns future{List()}
      func.apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]]) returns Results.Ok("call function")

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj("_id"->bson),"typ","")(func)}
      val r=call(action,req)
      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("call function")

      there was one(f.typeCardsDaoMock).findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"types"->"typ")), any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson)))
      there was one(f.cardDaoMock).countCards()
      there was one(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "Call function for print result if method is called with modele filter" in new WithApplication {
      val f=fixture
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"modele"->Json.obj("$regex"->".*mod.*"))), any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson))) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(List())) returns future{List()}
      func.apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]]) returns Results.Ok("call function")

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj("_id"->bson),"","mod")(func)}
      val r=call(action,req)
      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("call function")

      there was one(f.typeCardsDaoMock).findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"modele"->Json.obj("$regex"->".*mod.*"))), any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson)))
      there was one(f.cardDaoMock).countCards()
      there was one(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "Call function for print result if method is called with type and modele filter" in new WithApplication {
      val f=fixture
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"types"->"typ","modele"->Json.obj("$regex"->".*mod.*"))), any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson))) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countUsedCards(org.mockito.Matchers.eq(List())) returns future{List()}
      func.apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]]) returns Results.Ok("call function")

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj("_id"->bson),"typ","mod")(func)}
      val r=call(action,req)
      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("call function")

      there was one(f.typeCardsDaoMock).findAll(org.mockito.Matchers.eq(Json.obj("_id"->bson,"types"->"typ","modele"->Json.obj("$regex"->".*mod.*"))), any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(org.mockito.Matchers.eq(BSONDocument("_id"->bson)))
      there was one(f.cardDaoMock).countCards()
      there was one(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "send 500 internal error if mongoDB error when find all card type" in new WithApplication{
      val f=fixture
      val futureMock=mock[Future[List[TypeCards]]]
      val throwable=mock[Throwable]
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns futureMock
      futureMock.flatMap(any[List[TypeCards]=>Future[List[TypeCards]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj(),"","")(func)}
      val r=call(action,req)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(futureMock).flatMap(any[List[TypeCards]=>Future[List[TypeCards]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was no(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "send 500 internal error if mongoDB error when find all card type name" in new WithApplication{
      val f=fixture
      val futureMock=mock[Future[Stream[BSONDocument]]]
      val throwable=mock[Throwable]
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns futureMock
      futureMock.flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj(),"","")(func)}
      val r=call(action,req)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(futureMock).flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was no(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }

    "send 500 internal error if mongoDB error when find number of cards" in new WithApplication {
      val f=fixture
      val func=mock[(List[TypeCards],List[BSONDocument],List[BSONDocument],List[(BSONObjectID,Int)])=>Result]
      val futureMock=mock[Future[Stream[BSONDocument]]]
      val throwable=mock[Throwable]

      f.typeCardsDaoMock.findAll(any[JsObject], any[JsObject])(any[ExecutionContext]) returns future{List[TypeCards]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}
      f.cardDaoMock.countCards() returns futureMock
      futureMock.flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}

      val req=FakeRequest(GET, "/inventary/cards").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{f.controller.getInventaryTypeCards(Json.obj(),"","")(func)}
      val r=call(action,req)
      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findAll(any[JsObject], any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was one(f.cardDaoMock).countCards()
      there was one(futureMock).flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
      there was no(func).apply(any[List[TypeCards]],any[List[BSONDocument]],any[List[BSONDocument]],any[List[(BSONObjectID,Int)]])
    }
  }

  "When method printForm is called, TypeCardsManager" should{
    "print an empty form" in new WithApplication{
      val f=fixture

      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val req=FakeRequest(GET, "/inventary/cards/type").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{implicit request=>f.controller.printForm(Results.Ok,TypeCardsManager.form,mock[Call])}
      val r=call(action,req)


      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must contain("<input id=\"modele\" name=\"modele\" class=\"form-control\" list=\"list_modele\" type=\"text\" autofocus=\"autofocus\" autocomplete=\"off\" value=\"\"/>")
      content must contain("<input id=\"types\" name=\"types\" class=\"form-control\" list=\"list_type\" type=\"text\" autocomplete=\"off\" value=\"\"/>")

      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send Internal Error if mongoDB error when find list of modele" in new WithApplication{
      val f=fixture
      val futureMock=mock[Future[Stream[BSONDocument]]]
      val throwable=mock[Throwable]

      f.typeCardsDaoMock.findListModele() returns futureMock
      futureMock.flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}

      val req=FakeRequest(GET, "/inventary/cards/type").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{implicit request=>f.controller.printForm(Results.Ok,TypeCardsManager.form,mock[Call])}
      val r=call(action,req)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findListModele()
      there was one(futureMock).flatMap(any[Stream[BSONDocument]=>Future[Stream[BSONDocument]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }

    "send Internal Error if mongoDB error when find list of type" in new WithApplication{
      val f=fixture
      val futureMock=mock[Future[Stream[BSONDocument]]]
      val throwable=mock[Throwable]

      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns futureMock
      futureMock.map(any[Stream[BSONDocument]=>Stream[BSONDocument]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}
      val req=FakeRequest(GET, "/inventary/cards/type").withSession("user" -> """{"login":"test"}""")
      val action = Action.async{implicit request=>f.controller.printForm(Results.Ok,TypeCardsManager.form,mock[Call])}
      val r=call(action,req)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
      there was one(futureMock).map(any[Stream[BSONDocument]=>Stream[BSONDocument]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }
  }

  "When user submit a form, TypeCardsManager" should {
    "send bad_request with form and empty input" in new WithApplication {
      val route=mock[Call]
      val function=mock[TypeCardsForm=>Future[Result]]
      val function2=mock[TypeCardsForm=>JsObject]
      val f=fixture

      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val r = f.controller.submitForm("error",route)(function2)(function)(FakeRequest(POST, "url").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(BAD_REQUEST)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must contains("<span class=\"control-label errors\">This field is required</span>", 2)

      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send Bad_Request for existing type" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val route=mock[Call]
      val function=mock[TypeCardsForm=>Future[Result]]
      val function2=mock[TypeCardsForm=>JsObject]
      val f=fixture
      val typeCards=mock[TypeCards]

      function2.apply(any[TypeCardsForm]) returns Json.obj()
      f.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List(typeCards)}
      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val r = f.controller.submitForm("error",route)(function2)(function)(FakeRequest(POST, "url").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(BAD_REQUEST)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<div class=\"alert alert-danger\" role=\"alert\">Ce type de carte existe déjà</div>")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(function2).apply(any[TypeCardsForm])
      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send Bad_Request for existing type but delete" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val route=mock[Call]
      val function=mock[TypeCardsForm=>Future[Result]]
      val function2=mock[TypeCardsForm=>JsObject]
      val f=fixture
      val typeCards=TypeCards(bson,"mod","type",true)

      function2.apply(any[TypeCardsForm]) returns Json.obj()
      f.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List(typeCards)}
      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val r = f.controller.submitForm("error message",route)(function2)(function)(FakeRequest(POST, "url").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(BAD_REQUEST)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<div class=\"alert alert-danger\" role=\"alert\">error message</div>")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(function2).apply(any[TypeCardsForm])
      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send 500 Internal error for mongoDB error" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val route=mock[Call]
      val function=mock[TypeCardsForm=>Future[Result]]
      val function2=mock[TypeCardsForm=>JsObject]
      val futureMock=mock[Future[List[TypeCards]]]
      val fix=fixture
      val throwable=mock[Throwable]

      function2.apply(any[TypeCardsForm]) returns Json.obj()
      fix.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns futureMock
      futureMock.flatMap(any[List[TypeCards]=>Future[List[TypeCards]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})


      val r = fix.controller.submitForm("error",route)(function2)(function)(FakeRequest(POST, "url").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))


      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(function2).apply(any[TypeCardsForm])
      there was one(fix.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(futureMock).flatMap(any[List[TypeCards]=>Future[List[TypeCards]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }
  }

  "When user is on the resource /inventary/cards/type , TypeCardsManager" should {
    "send 200 on OK with an empty form" in new WithApplication {
      val f=fixture

      f.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      f.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val r = f.controller.typePage.apply(FakeRequest(GET, "/inventary/cards/type").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must contain("<input id=\"modele\" name=\"modele\" class=\"form-control\" list=\"list_modele\" type=\"text\" autofocus=\"autofocus\" autocomplete=\"off\" value=\"\"/>")
      content must contain("<input id=\"types\" name=\"types\" class=\"form-control\" list=\"list_type\" type=\"text\" autocomplete=\"off\" value=\"\"/>")

      there was one(f.typeCardsDaoMock).findListModele()
      there was one(f.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send redirect after insert type card" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val lastError=mock[LastError]
      val f=fixture

      f.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List()}
      f.typeCardsDaoMock.insert(any[TypeCards],any[GetLastError])(any[ExecutionContext]) returns future{lastError}

      val r = f.controller.typeInsert.apply(FakeRequest(POST, "/inventary/cards/type").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must beSome("/inventary/cards")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).insert(any[TypeCards],any[GetLastError])(any[ExecutionContext])
    }

    "send redirect after reactivat delete type card" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ","send":"Réactiver"}""")
      val lastError=mock[LastError]
      val f=fixture
      val typeCards=TypeCards(bson,"mod","type",true)

      f.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List(typeCards)}
      f.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{Some(typeCards)}
      f.typeCardsDaoMock.updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns future{lastError}

      val r = f.controller.typeInsert.apply(FakeRequest(POST, "/inventary/cards/type").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must beSome("/inventary/cards")

      there was one(f.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
    }

    "send internal server error if mongoDB error when insert card type" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val lastError=mock[Future[LastError]]
      val f=fixture
      val throwable=mock[Throwable]

      f.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List()}
      f.typeCardsDaoMock.insert(any[TypeCards],any[GetLastError])(any[ExecutionContext]) returns lastError
      lastError.map(any[LastError=>LastError])(any[ExecutionContext]) returns lastError
      lastError.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value => future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}

      val r = f.controller.typeInsert.apply(FakeRequest(POST, "/inventary/cards/type").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(f.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(f.typeCardsDaoMock).insert(any[TypeCards],any[GetLastError])(any[ExecutionContext])
      there was one(lastError).map(any[LastError=>LastError])(any[ExecutionContext])
      there was one(lastError).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }
  }

  "When user is on the resource /inventary/cards/:id/update , TypeCardsManager" should {
    "send 200 on OK with a form" in new WithApplication {
      val typeCards = Some(TypeCards(bson, "mod", "typ"))

      val fix = fixture

      fix.typeCardsDaoMock.findById(org.mockito.Matchers.eq(bson))(any[ExecutionContext]) returns future{typeCards}
      fix.typeCardsDaoMock.findListModele() returns future{Stream[BSONDocument]()}
      fix.typeCardsDaoMock.findListType(any[BSONDocument]) returns future{Stream[BSONDocument]()}

      val r = fix.controller.typeUpdatePage(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/" + bson.stringify + "/update").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(OK)
      contentType(r) must beSome.which(_ == "text/html")
      val content = contentAsString(r)
      content must contain("<title>Inventaire des cartes</title>")
      content must contain("<input id=\"modele\" name=\"modele\" class=\"form-control\" list=\"list_modele\" type=\"text\" autofocus=\"autofocus\" autocomplete=\"off\" value=\"mod\"/>")
      content must contain("<input id=\"types\" name=\"types\" class=\"form-control\" list=\"list_type\" type=\"text\" autocomplete=\"off\" value=\"typ\"/>")

      there was one(fix.typeCardsDaoMock).findById(org.mockito.Matchers.eq(bson))(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).findListModele()
      there was one(fix.typeCardsDaoMock).findListType(any[BSONDocument])
    }

    "send Redirect for type card not found" in new WithApplication {
      val fix=fixture

      fix.typeCardsDaoMock.findById(org.mockito.Matchers.eq(bson))(any[ExecutionContext]) returns future{None}

      val r = fix.controller.typeUpdatePage(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/update").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.typeCardsDaoMock).findById(org.mockito.Matchers.eq(bson))(any[ExecutionContext])
    }

    "send 500 Internal error for mongoDB error when find the card type" in new WithApplication {
      val future_Mock=mock[Future[Option[TypeCards]]]
      val throwable=mock[Throwable]

      val fix=fixture

      fix.typeCardsDaoMock.findById(org.mockito.Matchers.eq(bson))(any[ExecutionContext]) returns future_Mock
      future_Mock.flatMap(any[(Option[TypeCards])=>Future[Option[TypeCards]]])(any[ExecutionContext]) returns future_Mock
      future_Mock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})

      val r = fix.controller.typeUpdatePage(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/update").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.typeCardsDaoMock).findById(any[BSONObjectID])(any[ExecutionContext])
      there was one(future_Mock).flatMap(any[(Option[TypeCards])=>Future[Option[TypeCards]]])(any[ExecutionContext])
      there was one(future_Mock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }

    "send redirect after update type card" in new WithApplication {
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val fix=fixture
      val lastError=mock[LastError]

      fix.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List()}
      fix.typeCardsDaoMock.updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns future{lastError}

      val r = fix.controller.typeUpdate(bson.stringify).apply(FakeRequest(POST, "/inventary/cards/"+bson.stringify+"/update").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
    }

    "send 500 Internal error for mongoDB error when update the card type" in new WithApplication {
      val future_Mock=mock[Future[LastError]]
      val formData = Json.parse("""{"modele":"mod","types":"typ"}""")
      val throwable=mock[Throwable]

      val fix=fixture

      fix.typeCardsDaoMock.findAll(any[JsObject],any[JsObject])(any[ExecutionContext]) returns future{List()}
      fix.typeCardsDaoMock.updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns future_Mock
      future_Mock.map(any[LastError=>LastError])(any[ExecutionContext]) returns future_Mock
      future_Mock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})

      val r = fix.controller.typeUpdate(bson.stringify).apply(FakeRequest(POST, "/inventary/cards/"+bson.stringify+"/update").withJsonBody(formData).withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.typeCardsDaoMock).findAll(any[JsObject],any[JsObject])(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).updateById(org.mockito.Matchers.eq(bson),any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
      there was one(future_Mock).map(any[LastError=>LastError])(any[ExecutionContext])
      there was one(future_Mock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }
  }

  "When method updateWithDeleteColumn is called, TypeCardsManager" should{
    "send 500 Internal Error for mongoDB error when find card type" in new WithApplication{
      val futureMock = mock[Future[Option[TypeCards]]]
      val fix=fixture
      val throwable=mock[Throwable]

      fix.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns futureMock
      futureMock.flatMap(any[(Option[TypeCards])=>Future[Option[TypeCards]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})

      val r = fix.controller.updateWithColumnDelete(Json.obj(),true)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(futureMock).flatMap(any[(Option[TypeCards])=>Future[Option[TypeCards]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }

    "send 500 Internal Error for mongoDB error when update card type" in new WithApplication{
      val futureMock=mock[Future[LastError]]
      val fix=fixture
      val throwable=mock[Throwable]

      fix.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{Some(TypeCards(bson,"mod","type"))}
      fix.typeCardsDaoMock.updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns futureMock
      futureMock.map(any[LastError=>LastError])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})

      val r = fix.controller.updateWithColumnDelete(Json.obj(),true)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
      there was one(futureMock).map(any[LastError=>LastError])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Results]])(any[ExecutionContext])
    }

    "send Redirect for type card not found" in new WithApplication{
      val fix=fixture

      fix.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{None}

      val r = fix.controller.updateWithColumnDelete(Json.obj(),true)

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
    }

    "send Redirect after update type card" in new WithApplication{
      val fix=fixture
      val lastError=mock[LastError]

      fix.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{Some(TypeCards(bson,"mod","type"))}
      fix.typeCardsDaoMock.updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns future{lastError}

      val r = fix.controller.updateWithColumnDelete(Json.obj(),true)

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
    }
  }

  "When user delete a type card, TypeCardsManager" should {

    "send 500 Internal Error for mongoDB error when find card" in new WithApplication{
      val futureMock=mock[Future[Option[Cards]]]
      val fix=fixture
      val throwable=mock[Throwable]

      fix.cardDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns futureMock
      futureMock.flatMap(any[Option[Cards]=>Future[Option[Cards]]])(any[ExecutionContext]) returns futureMock
      futureMock.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers (vals => future{vals.asInstanceOf[PartialFunction[Throwable,Result]](throwable)})

      val r = fix.controller.delete(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/delete").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.cardDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(futureMock).flatMap(any[Option[Cards]=>Future[Option[Cards]]])(any[ExecutionContext])
      there was one(futureMock).recover(any[PartialFunction[Throwable,Results]])(any[ExecutionContext])
    }

    "send BadRequest if card found" in new WithApplication{
      val fix=fixture

      fix.cardDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{Some(Cards(bson2,"Id",bson,bson,new Date(),None,true,Some("v01"),true,Some("un com")))}

      val r = fix.controller.delete(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/delete").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.cardDaoMock).findOne(any[JsObject])(any[ExecutionContext])
    }

    "send Redirect after update type card" in new WithApplication{
      val fix=fixture
      val lastError=mock[LastError]

      fix.typeCardsDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{Some(TypeCards(bson,"mod","type"))}
      fix.cardDaoMock.findOne(any[JsObject])(any[ExecutionContext]) returns future{None}
      fix.typeCardsDaoMock.updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext]) returns future{lastError}

      val r = fix.controller.delete(bson.stringify).apply(FakeRequest(GET, "/inventary/cards/"+bson.stringify+"/delete").withSession("user" -> """{"login":"test"}"""))

      status(r) must equalTo(SEE_OTHER)
      header("Location",r) must equalTo(Some("/inventary/cards"))

      there was one(fix.typeCardsDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(fix.cardDaoMock).findOne(any[JsObject])(any[ExecutionContext])
      there was one(fix.typeCardsDaoMock).updateById(any[BSONObjectID],any[TypeCards],any[GetLastError])(any[Writes[TypeCards]],any[ExecutionContext])
    }
  }

  "When method doIfTypeCardsFound is called, TypeCardsManager" should{
    "call function notFound if type cards not found" in new WithApplication{
      val fix=fixture
      val found=mock[TypeCards=>Future[Result]]
      val notFound=mock[Unit=>Future[Result]]

      fix.typeCardsDaoMock.findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext]) returns future{None}
      notFound.apply(any[Unit]) returns future{Results.Ok("exec notFound")}

      val r=fix.controller.doIfTypeCardsFound(bson)(found)(notFound)

      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("exec notFound")

      there was one(fix.typeCardsDaoMock).findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext])
      there was one(notFound).apply(any[Unit])
      there was no(found).apply(any[TypeCards])
    }

    "call function found if type cards found" in new WithApplication{
      val fix=fixture
      val found=mock[TypeCards=>Future[Result]]
      val notFound=mock[Unit=>Future[Result]]
      val typeCards=mock[TypeCards]

      fix.typeCardsDaoMock.findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext]) returns future{Some(typeCards)}
      found.apply(any[TypeCards]) returns future{Results.Ok("exec found")}

      val r=fix.controller.doIfTypeCardsFound(bson)(found)(notFound)

      status(r) must equalTo(OK)
      contentAsString(r) must equalTo("exec found")

      there was one(fix.typeCardsDaoMock).findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext])
      there was no(notFound).apply(any[Unit])
      there was one(found).apply(any[TypeCards])
    }

    "send internal server error if have mongoDB error when find type cards" in new WithApplication{
      val fix=fixture
      val found=mock[TypeCards=>Future[Result]]
      val notFound=mock[Unit=>Future[Result]]
      val lastError=mock[Future[Option[TypeCards]]]
      val throwable=mock[Throwable]

      fix.typeCardsDaoMock.findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext]) returns lastError
      lastError.flatMap(any[Option[TypeCards]=>Future[Option[TypeCards]]])(any[ExecutionContext]) returns lastError
      lastError.recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext]) answers {value=>future{value.asInstanceOf[PartialFunction[Throwable,Result]](throwable)}}

      val r=fix.controller.doIfTypeCardsFound(bson)(found)(notFound)

      status(r) must equalTo(INTERNAL_SERVER_ERROR)

      there was one(fix.typeCardsDaoMock).findOne(org.mockito.Matchers.eq(Json.obj("_id"->bson,"delete"->false)))(any[ExecutionContext])
      there was no(notFound).apply(any[Unit])
      there was no(found).apply(any[TypeCards])
      there was one(lastError).flatMap(any[Option[TypeCards]=>Future[Option[TypeCards]]])(any[ExecutionContext])
      there was one(lastError).recover(any[PartialFunction[Throwable,Result]])(any[ExecutionContext])
    }
  }

  "When method filtreStock is called, TypeCardsManager" should{
    "return true if the value is greater than 0 and filter is equal to yes" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("yes")(1) must beTrue
    }

    "return false if the value is less or equal than 0 and filter is equal to yes" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("yes")(0) must beFalse
    }

    "return true if the value is equal to 0 and filter is equal to no" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("no")(0) must beTrue
    }

    "return false if the value is not equal to 0 and filter is equal to no" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("no")(1) must beFalse
    }

    "return true if the value is greater or equal than 0 and filter is not equal to yes or no" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("yesNo")(0) must beTrue
    }

    "return false if the value is less than 0 and filter is not equal to yes or no" in new WithApplication{
      val fix=fixture

      fix.controller.filtreStock("yesNo")(-1) must beFalse
    }
  }
}