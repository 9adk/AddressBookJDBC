package com.capgemini.addressbooktest;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import com.capgemini.addressbookdb.AddressBookService;
import com.capgemini.addressbookdb.Contact;
import com.capgemini.addressbookdb.DatabaseException;
import com.capgemini.addressbookdb.AddressBookService.IOService;
import com.google.gson.Gson;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class AddressBookTest {
	
	@Test
	public void givenContactDataInDB_WhenRetrieved_ShouldMatchContactCount() throws DatabaseException {
		AddressBookService addressBookService = new AddressBookService();
		List<Contact> contactData = addressBookService.readContactData(IOService.DB_IO);
		assertEquals(4, contactData.size());
	}

	@Test
	public void givenNewDataForContact_WhenUpdated_ShouldBeInSync() throws DatabaseException, SQLException {
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		addressBookService.updatePersonsPhone("Aditya Kharade", 8850273350L);
		addressBookService.readContactData(IOService.DB_IO);
		boolean result = addressBookService.checkContactDataSync("Aditya Kharade");
		assertEquals(true, result);
	}

	
	@Test
	public void givenContactInDB_WhenRetrievedForDateRange_ShouldMatchContactCount() throws DatabaseException {
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		List<Contact> resultList = addressBookService.getContactForDateRange(LocalDate.of(2020, 01, 01),
				LocalDate.of(2021, 01, 01));
		assertEquals(1, resultList.size());
	}

	
	@Test
	public void givenContactInDB_WhenRetrievedForCityAndState_ShouldMatchContactCount() throws DatabaseException {
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		List<Contact> resultList = addressBookService.getContactForCityAndState("Akola", "Maharashta");
		assertEquals(2, resultList.size());
	}

	
	@Test
	public void givenContactInDB_WhenAdded_ShouldBeAddedInSingleTransaction() throws DatabaseException, SQLException {
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		addressBookService.addContactInDatabase("Aniket","Sarap", "Kaulkhed", 444001L, "Akola", "Maharashtra",
                                               8844557722L, "abc@d.com", LocalDate.of(2021, 01, 01), 1);
		boolean result = addressBookService.checkContactDataSync("Aniket");
		assertEquals(true, result);
	}
	@Test
	public void geiven2Contacts_WhenAddedToDB_ShouldMatchContactEntries() throws DatabaseException {
		Contact[] contactArray = { new Contact("Aniket","Sarap","Kaulkhed", "Akola","Maharashtra", 444001L, 8850273350L,"abcd@gmail.com",LocalDate.of(2021, 01, 01),2),
				new Contact("Sachin","Badhe","Kaulkhed", "Akola","Maharashtra", 444001L, 7887483853L,"abcd@gmail.com",LocalDate.of(2021, 01, 01),2)};
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		Instant start = Instant.now();
		addressBookService.addContactToDB(Arrays.asList(contactArray));
		Instant threadEnd = Instant.now();
		System.out.println("Duration with Thread: " + Duration.between(start, threadEnd));
		long result = addressBookService.countEntries(IOService.DB_IO);
		assertEquals(3, result);
	}
	@Test
	public void geiven2Persons_WhenUpdatedPhoneNumer_ShouldSyncWithDB() throws DatabaseException {
		Map<String, Long> contactMap = new HashMap<>();
		contactMap.put("Aniket Sarap",7788996633L);
		contactMap.put("Sachin Badhe",4455776699L);
		AddressBookService addressBookService = new AddressBookService();
		addressBookService.readContactData(IOService.DB_IO);
		Instant start = Instant.now();
		addressBookService.updatePhoneNumber(contactMap);
		Instant end = Instant.now();
		System.out.println("Duration with Thread: " + Duration.between(start, end));
		boolean result = addressBookService.checkContactInSyncWithDB(Arrays.asList("Aniket Sarap"));
		assertEquals(true,result);
	}
	
	@Before
	public void setup() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = 3000;
	}
	
	private Contact[] getContactList() {
		Response response = RestAssured.get("/contact");
		System.out.println("Contact entries in JSONServer:\n"+response.asString());
		Contact[] arrayOfContact = new Gson().fromJson(response.asString(),Contact[].class);
		return arrayOfContact;
	}
	private Response addContactToJsonServer(Contact contact) {
		String contactJson = new Gson().toJson(contact);
		RequestSpecification request = RestAssured.given();
		request.header("Content-Type", "application/json");
		request.body(contactJson);
		return request.post("/contact");
	}
	
	@Test
	public void givenContactDataInJSONServer_WhenRetrieved_ShouldMatchTheCount() {
		Contact[] arrayOfContact = getContactList();
		AddressBookService addressBookService = new AddressBookService(Arrays.asList(arrayOfContact));
		long entries = addressBookService.countEntries(IOService.REST_IO);
		assertEquals(1,entries);
	}
	
	@Test
	public void givenListOfNewContacts_WhenAdded_ShouldMatch201ResponseAndCount() {
		Contact[] arrayOfContact = getContactList();
		AddressBookService addService = new AddressBookService(Arrays.asList(arrayOfContact));
		Contact[] arrayOfCon = {new Contact("Aniket","Sarap","Kaulkhed", "Akola","Maharashtra", 444001L, 8850273350L,"abcd@gmail.com",LocalDate.of(2021, 01, 01),2),
				new Contact("Sachin","Badhe","Kaulkhed", "Akola","Maharashtra", 444001L, 7887483853L,"abcd@gmail.com",LocalDate.of(2021, 01, 01),2)};
		List<Contact> contactList = Arrays.asList(arrayOfCon);
		contactList.forEach(contact -> {
			Runnable task = () -> {
				Response response = addContactToJsonServer(contact);
				int statusCode = response.getStatusCode();
				assertEquals(201, statusCode);
				Contact newContact = new Gson().fromJson(response.asString(), Contact.class);
				addService.addContactToAddressBook(newContact);
			};
			Thread thread = new Thread(task, contact.firstName);
			thread.start();
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		long count = addService.countEntries(IOService.REST_IO);
		assertEquals(3, count);
	}
}
