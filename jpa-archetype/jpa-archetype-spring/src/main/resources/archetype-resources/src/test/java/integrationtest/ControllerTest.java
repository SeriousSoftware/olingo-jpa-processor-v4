package ${package}.integrationtest;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import ${package}.SpringApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.WebApplicationContext;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.path.xml.XmlPath;
import io.restassured.path.xml.element.Node;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SpringApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.config.location=classpath:application-test.yml" })
public class ControllerTest {

  @Autowired
  private WebApplicationContext context;
  @LocalServerPort
  private int port;

  @BeforeEach
  public void setup() {
    RestAssuredMockMvc.webAppContextSetup(context);
  }

  @Test
  public void testRetrieveServiceDocument() {
    final String xml = given()
        .accept(ContentType.XML)
        .when()
        .get("/${punit}/v1/")
        .then()
        .statusCode(HttpStatusCode.OK.getStatusCode())
        .contentType(ContentType.XML)
        .extract()
        .asString();

    final XmlPath path = new XmlPath(xml);
    final Collection<Node> n = ((Node) ((Node) path.get("service")).get("workspace")).get("collection");
    assertNotNull(n);
    assertFalse(n.isEmpty());
  }

  @Test
  public void testRetrieveMetadataDocument() {
    final String xml = given()
        .when()
        .get("/${punit}/v1/$metadata")
        .then()
        .statusCode(HttpStatusCode.OK.getStatusCode())
        .contentType(ContentType.XML)
        .extract()
        .asString();

    final XmlPath path = new XmlPath(xml);
    final Node n = ((Node) ((Node) path.get("edmx:Edmx")).get("DataServices")).get("Schema");
    assertNotNull(n);
    assertEquals("${punit}", n.getAttribute("Namespace"));
    assertNotNull(n.get("EntityContainer"));
  }

  @Test
  public void testCreateInstance() {
    given()
        .contentType("application/json")
        .body("{ \"Id\" : \"1\" }")
        .when()
        .post("/${punit}/v1/${entity-table}s")
        .then()
        .statusCode(HttpStatusCode.CREATED.getStatusCode());
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/${punit}/v1/${entity-table}s('1')")
        .then()
        .statusCode(HttpStatusCode.OK.getStatusCode());
  }

  @Test
  public void testCreateInstanceWithBatch() throws URISyntaxException {

    URI uri = getClass().getClassLoader()
        .getResource("requests/CreateEntityViaBatch.txt").toURI();

    File myFile = new File(uri);
    final String responce = given()
        .contentType("multipart/mixed;boundary=abc")
        .body(myFile)
        .when()
        .post("/${punit}/v1/$batch")
        .then()
        .statusCode(HttpStatusCode.ACCEPTED.getStatusCode())
        .extract()
        .asString();

    given()
        .accept(ContentType.JSON)
        .when()
        .get("/${punit}/v1/${entity-table}s('2')")
        .then()
        .statusCode(HttpStatusCode.OK.getStatusCode());

    final String[] partResults = responce.split("--changeset");
    assertTrue(partResults[1].contains("HTTP/1.1 201"));
    assertTrue(partResults[2].contains("HTTP/1.1 400"));
  }

  @Test
  public void testCreateInstanceDeep() {
    given()
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .body("{ \"Id\" : \"3\", \"ValueObjects\" : [{\"Id\" : \"1\"}, {\"Id\" : \"2\"}] }")
        .when()
        .post("/${punit}/v1/${entity-table}s")
        .then()
        .statusCode(HttpStatusCode.CREATED.getStatusCode())
        .body("ValueObjects.Id", hasItems("1", "2"))
        .body("Id", equalTo("3"))
        .extract()
        .asString();
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/${punit}/v1/${value-object-table}s(EntityId='3',Id='2')")
        .then()
        .statusCode(HttpStatusCode.OK.getStatusCode());

  }

  @AfterEach
  public void teardown() {
    RestAssuredMockMvc.reset();
  }
}
