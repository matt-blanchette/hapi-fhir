package ca.uhn.fhir.jpa.dao.dstu21;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.dstu21.model.Bundle;
import org.hl7.fhir.dstu21.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu21.model.IdType;
import org.hl7.fhir.dstu21.model.Observation;
import org.hl7.fhir.dstu21.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu21.model.OperationOutcome;
import org.hl7.fhir.dstu21.model.Organization;
import org.hl7.fhir.dstu21.model.Patient;
import org.hl7.fhir.dstu21.model.StructureDefinition;
import org.hl7.fhir.dstu21.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.Ignore;
import org.junit.Test;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.ValidationModeEnum;
import ca.uhn.fhir.rest.server.EncodingEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;

public class FhirResourceDaoDstu21ValidateTest extends BaseJpaDstu21Test {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(FhirResourceDaoDstu21ValidateTest.class);

	@Test
	@Ignore
	public void testValidateResourceContainingProfileDeclarationJson() throws Exception {
		String methodName = "testValidateResourceContainingProfileDeclarationJson";
		OperationOutcome outcome = doTestValidateResourceContainingProfileDeclaration(methodName, EncodingEnum.JSON);

		String ooString = myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);
		ourLog.info(ooString);
		assertThat(ooString, containsString("Element '.subject': minimum required = 1, but only found 0"));
		assertThat(ooString, containsString("Element encounter @ : max allowed = 0, but found 1"));
		assertThat(ooString, containsString("Element '.device': minimum required = 1, but only found 0"));
	}

	@Test
	@Ignore
	public void testValidateResourceContainingProfileDeclarationXml() throws Exception {
		String methodName = "testValidateResourceContainingProfileDeclarationXml";
		OperationOutcome outcome = doTestValidateResourceContainingProfileDeclaration(methodName, EncodingEnum.XML);

		String ooString = myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);
		ourLog.info(ooString);
		assertThat(ooString, containsString("Element '/f:Observation.subject': minimum required = 1, but only found 0"));
		assertThat(ooString, containsString("Element encounter @ /f:Observation: max allowed = 0, but found 1"));
		assertThat(ooString, containsString("Element '/f:Observation.device': minimum required = 1, but only found 0"));
	}

	private OperationOutcome doTestValidateResourceContainingProfileDeclaration(String methodName, EncodingEnum enc) throws IOException {
		Bundle vss = loadResourceFromClasspath(Bundle.class, "/org/hl7/fhir/instance/model/dstu21/valueset/valuesets.xml");
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-status"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-category"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-codes"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-methods"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-valueabsentreason"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-interpretation"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "body-site"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "referencerange-meaning"));
		myValueSetDao.update((ValueSet) findResourceByIdInBundle(vss, "observation-relationshiptypes"));

		StructureDefinition sd = loadResourceFromClasspath(StructureDefinition.class, "/org/hl7/fhir/instance/model/dstu21/profile/devicemetricobservation.profile.xml");
		sd.setId(new IdType());
		sd.setUrl("http://example.com/foo/bar/" + methodName);
		myStructureDefinitionDao.create(sd);

		Observation input = new Observation();
		input.getMeta().getProfile().add(new IdType(sd.getUrl()));

		input.addIdentifier().setSystem("http://acme").setValue("12345");
		input.getEncounter().setReference("http://foo.com/Encounter/9");
		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		String encoded = null;
		MethodOutcome outcome = null;
		ValidationModeEnum mode = ValidationModeEnum.CREATE;
		switch (enc) {
		case JSON:
			encoded = myFhirCtx.newJsonParser().encodeResourceToString(input);
			try {
				myObservationDao.validate(input, null, encoded, EncodingEnum.JSON, mode, null);
				fail();
			} catch (PreconditionFailedException e) {
				return (OperationOutcome) e.getOperationOutcome();
			}
		case XML:
			encoded = myFhirCtx.newXmlParser().encodeResourceToString(input);
			try {
				myObservationDao.validate(input, null, encoded, EncodingEnum.XML, mode, null);
				fail();
			} catch (PreconditionFailedException e) {
				return (OperationOutcome) e.getOperationOutcome();
			}
		}

		throw new IllegalStateException(); // shouldn't get here
	}

	@Test
	public void testValidateResourceContainingProfileDeclarationInvalid() throws Exception {
		String methodName = "testValidateResourceContainingProfileDeclarationInvalid";

		Observation input = new Observation();
		String profileUri = "http://example.com/" + methodName;
		input.getMeta().getProfile().add(new IdType(profileUri));

		input.addIdentifier().setSystem("http://acme").setValue("12345");
		input.getEncounter().setReference("http://foo.com/Encounter/9");
		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		ValidationModeEnum mode = ValidationModeEnum.CREATE;
		String encoded = myFhirCtx.newJsonParser().encodeResourceToString(input);
		MethodOutcome outcome = myObservationDao.validate(input, null, encoded, EncodingEnum.JSON, mode, null);

		String ooString = myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome.getOperationOutcome());
		ourLog.info(ooString);
		assertThat(ooString, containsString("StructureDefinition reference \\\"" + profileUri + "\\\" could not be resolved"));

	}

	@Test
	public void testValidateForCreate() {
		String methodName = "testValidateForCreate";

		Patient pat = new Patient();
		pat.setId("Patient/123");
		pat.addName().addFamily(methodName);

		try {
			myPatientDao.validate(pat, null, null, null, ValidationModeEnum.CREATE, null);
			fail();
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage(), containsString("ID must not be populated"));
		}

		pat.setId("");
		myPatientDao.validate(pat, null, null, null, ValidationModeEnum.CREATE, null);

	}

	@Test
	public void testValidateForUpdate() {
		String methodName = "testValidateForUpdate";

		Patient pat = new Patient();
		pat.setId("Patient/123");
		pat.addName().addFamily(methodName);
		myPatientDao.validate(pat, null, null, null, ValidationModeEnum.UPDATE, null);

		pat.setId("");

		try {
			myPatientDao.validate(pat, null, null, null, ValidationModeEnum.UPDATE, null);
			fail();
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage(), containsString("ID must be populated"));
		}

	}

	@Test
	public void testValidateForUpdateWithContained() {
		String methodName = "testValidateForUpdate";

		Organization org = new Organization();
		org.setId("#123");

		Patient pat = new Patient();
		pat.setId("Patient/123");
		pat.addName().addFamily(methodName);
		myPatientDao.validate(pat, null, null, null, ValidationModeEnum.UPDATE, null);

		pat.setId("");

		try {
			myPatientDao.validate(pat, null, null, null, ValidationModeEnum.UPDATE, null);
			fail();
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage(), containsString("ID must be populated"));
		}

	}

	@Test
	public void testValidateForDelete() {
		String methodName = "testValidateForDelete";

		Organization org = new Organization();
		org.setName(methodName);
		IIdType orgId = myOrganizationDao.create(org).getId().toUnqualifiedVersionless();

		Patient pat = new Patient();
		pat.addName().addFamily(methodName);
		pat.getManagingOrganization().setReference(orgId.getValue());
		IIdType patId = myPatientDao.create(pat).getId().toUnqualifiedVersionless();

		OperationOutcome outcome = null;
		try {
			myOrganizationDao.validate(null, orgId, null, null, ValidationModeEnum.DELETE, null);
			fail();
		} catch (ResourceVersionConflictException e) {
			outcome = (OperationOutcome) e.getOperationOutcome();
		}

		String ooString = myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);
		ourLog.info(ooString);
		assertThat(ooString, containsString("Unable to delete Organization"));

		pat.setId(patId);
		pat.getManagingOrganization().setReference("");
		myPatientDao.update(pat);

		outcome = (OperationOutcome) myOrganizationDao.validate(null, orgId, null, null, ValidationModeEnum.DELETE, null).getOperationOutcome();
		ooString = myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);
		ourLog.info(ooString);
		assertThat(ooString, containsString("Ok to delete"));

	}

	private IBaseResource findResourceByIdInBundle(Bundle vss, String name) {
		IBaseResource retVal = null;
		for (BundleEntryComponent next : vss.getEntry()) {
			if (next.getResource().getIdElement().getIdPart().equals(name)) {
				retVal = next.getResource();
				break;
			}
		}
		if (retVal == null) {
			fail("Can't find VS: " + name);
		}
		return retVal;
	}
	
	@Test
	public void testValidateNewQuestionnaireFormat() throws Exception {
		String input =IOUtils.toString(FhirResourceDaoDstu21ValidateTest.class.getResourceAsStream("/questionnaire_dstu21.xml"));
		try {
		MethodOutcome results = myQuestionnaireDao.validate(null, null, input, EncodingEnum.XML, ValidationModeEnum.UPDATE, null);
		OperationOutcome oo = (OperationOutcome) results.getOperationOutcome();
		ourLog.info(myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(oo));
		} catch (PreconditionFailedException e) {
			// this is a failure of the test
			ourLog.info(myFhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(e.getOperationOutcome()));
			throw e;
		}
	}

}
