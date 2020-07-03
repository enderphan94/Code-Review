package mt.gov.ha.rent.chaincode;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

//import javax.xml.bind.DatatypeConverter;

import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

//import org.json.JSONArray;
//import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import mt.gov.ha.rent.chaincode.RentContract.Status;

@Contract(name = "mt.gov.ha.rent.RentSmartContract", info = @Info(title = "Rent contract", description = "", version = "1.0", license = @License(name = "SPDX-License-Identifier: ", url = ""), contact = @Contact(email = "java-contract@example.com", name = "java-contract", url = "http://java-contract.me")))
@Default
public class RentSmartContract implements ContractInterface {

	private final static Logger logger = Logger.getLogger(RentSmartContract.class.getName());

	public static String lowerReference = "A";
	public static String higherReference = "Z";

	public RentSmartContract() {

	}

	@Transaction
	public boolean contractExists(org.hyperledger.fabric.contract.Context ctx, String reference) {
		byte[] buffer = ctx.getStub().getState(reference);
		return (buffer != null && buffer.length > 0);
	}

	@Transaction
	public void registerContractValidateData(org.hyperledger.fabric.contract.Context ctx, String reference, String idString, String lease_typeString, Boolean renewable, String statusString,
			String property_id, String street_id, String street_name, String post_code, String door_number, String house_name, String entrance_number, String block, String flat_number,
			String locality_id, String locality_name, String region_id, String property_type, Integer bedrooms, Integer occupants, Double floor_area, Boolean renewal_of_nonrenewable, Boolean sublet,
			String sublet_original_contract_reference, String renewal_nonrenewable_original_contract_reference, String renewal_preceeding_contract_reference, String registration_date,
			String commencement_date, String termination_date, String payment_frequencyString, Long payment_term, Long deposit_amount, String note, Boolean feesPaymentDone) {

		reference = reference.toUpperCase().trim();

		if (reference != null && !reference.equals("") && contractExists(ctx, reference))
			throw new ChaincodeException("The contract " + reference + " is already registered");

		RentContract.LeaseType lease_type = RentContract.LeaseType.valueOf(lease_typeString);
		RentContract.Status status = RentContract.Status.valueOf(statusString);
		RentContract.PaymentFrequency.valueOf(payment_frequencyString);// Validates the string

		if (status.equals(RentContract.Status.Refused) && (note == null || note.trim().equals("")))
			throw new ChaincodeException("Contract " + reference + ": Status " + status + " requires content for the note property");

		if (!lease_type.equals(RentContract.LeaseType.LR) && reference != null && !reference.equals(""))
			if (!lease_type.toString().equals(reference.substring(0, 2)))
				throw new ChaincodeException("Contract " + reference + " not matching contract lease type: " + lease_type);

		// 1. A long let must have a minimum duration of 12 months
		// Test with commencement date 1 january 2020 and termination date 31 December 2020
		if (lease_type.equals(RentContract.LeaseType.LL)) {
			LocalDate termination_LocalDate = LocalDate.parse(termination_date);

			LocalDate commencement_datePlusOneYearMinusOneDay = LocalDate.parse(commencement_date).plusYears(1).minusDays(1);
			if (termination_LocalDate.isBefore(commencement_datePlusOneYearMinusOneDay))
				throw new ChaincodeException("Contract " + reference + ": A longlet must have a minimum duration of 12 months");
		}

		// 4.Contracts cannot have a commencement date prior to 31/5/1995 (date included)
		LocalDate commencement_LocalDate = LocalDate.parse(commencement_date);
		if (commencement_LocalDate.isBefore(LocalDate.of(1995, 5, 31).plusDays(1)))
			throw new ChaincodeException("Contract " + reference + ": A commencement date mustn't prior or equals to 31/5/1995");

		// 2. A short let (SL) must have a duration of exactly 6 months
		if (lease_type.equals(RentContract.LeaseType.SL)) {
			LocalDate termination_LocalDate = LocalDate.parse(termination_date);
			LocalDate commencement_datePlusOneYearMinusOneDay = LocalDate.parse(commencement_date).plusMonths(6).minusDays(1);
			if (!termination_LocalDate.isEqual(commencement_datePlusOneYearMinusOneDay))
				throw new ChaincodeException("Contract " + reference + ": A shortlet must have exactly a duration of 6 months");
		}

		// 3. A shared space (SS) must have a duration of exactly 6 months
		if (lease_type.equals(RentContract.LeaseType.SS)) {
			LocalDate termination_LocalDate = LocalDate.parse(termination_date);
			LocalDate commencement_datePlusOneYearMinusOneDay = LocalDate.parse(commencement_date).plusMonths(6).minusDays(1);
			if (!termination_LocalDate.isEqual(commencement_datePlusOneYearMinusOneDay))
				throw new ChaincodeException("Contract " + reference + ": A shared space must have exactly a duration of 6 months");
		}

		// 5. A new contract can only have following status values: Active/Refused
		if (!status.equals(RentContract.Status.Active) && !status.equals(RentContract.Status.Refused))
			throw new ChaincodeException("Contract " + reference + ": Status " + status + " not supported on Contract register method");

		// 6. Termination date of contract cannot be a past date
		LocalDate termination_LocalDate = LocalDate.parse(termination_date);
		if (termination_LocalDate.isBefore(LocalDate.now()))
			throw new ChaincodeException("Contract " + reference + ": A Termination date of contract cannot be a past date");

		// 7. Contracts signed before 1/1/2020 cannot have a termination date before 1/1/2021 (Transition period)
		if (commencement_LocalDate.isBefore(LocalDate.of(2020, 1, 1)) && termination_LocalDate.isBefore(LocalDate.of(2021, 1, 1)))
			throw new ChaincodeException("Contract " + reference + ": Contracts signed before 1/1/2020 cannot have a termination date before 1/1/2021");

		// 8. For contracts that fall under the transitory period, late payment fee will not apply unless they register after 1/1/2021
		// if (registration_LocalDate.isBefore(LocalDate.of(2021, 1, 1)) && (feesPaymentDone != null && feesPaymentDone.equals(Boolean.TRUE)))
		// throw new ChaincodeException("Late fees payment is not applied on contracts registered before 1/1/2021");

		// 9. Contract registration cannot proceed if there are outstanding fees.
		if ((feesPaymentDone == null || feesPaymentDone.equals(Boolean.FALSE)))
			throw new ChaincodeException("Contract " + reference + ": Contract registration cannot proceed if there are outstanding fees.");

		// 12. If previous contract is passed to the registerContract, original contract should be LL and non renewable, all LL validations will apply plus: the transaction can only be accepted if it
		// is at least 4 months prior to original contract expiration date. (non renewable reweal and commencement date needs to be termination of previous contract +1). New LL points to previous one,
		// not original one.

		if (renewal_preceeding_contract_reference != null && !renewal_preceeding_contract_reference.trim().isEmpty()) {
			// Get original contract and check if it LL or not and it must be non renewable
			// Note: in some cases preceding contract reference maybe same as original contract reference (for example in first renew of contract)
			// Check if submission date or registration date is 4 months prior to previous contract expiration date

			// Make sure new LL pointing to previous one not original one

			try {
				ObjectMapper objectMapper = new ObjectMapper();
				RentContract originalRentalContract = objectMapper.readValue(ctx.getStub().getState(renewal_nonrenewable_original_contract_reference), RentContract.class);
				if (!originalRentalContract.getLease_type().equals(RentContract.LeaseType.LL))
					throw new ChaincodeException("The original contract " + renewal_nonrenewable_original_contract_reference + " is not LL. It can't be renewed registering a new contract.");
				if (originalRentalContract.getRenewable() != null && originalRentalContract.getRenewable().equals(Boolean.TRUE))
					throw new ChaincodeException("The original contract " + renewal_nonrenewable_original_contract_reference + " is renewable. It can't be renewed registering a new contract.");
				RentContract previousRentalContract = objectMapper.readValue(ctx.getStub().getState(renewal_preceeding_contract_reference), RentContract.class);
				if (!previousRentalContract.getRenewal_original_contract_reference().equals(originalRentalContract.getReference())
						&& !renewal_preceeding_contract_reference.equals(renewal_nonrenewable_original_contract_reference))
					throw new ChaincodeException("The original contract " + renewal_nonrenewable_original_contract_reference
							+ " is not the same for previous contract original reference or original reference is not the same as previous reference.");
				if (!LocalDate.parse(registration_date).isAfter(LocalDate.parse(previousRentalContract.getTermination_date()).minusMonths(4).plusDays(1)))
					throw new ChaincodeException("The transaction can only be accepted if it is at least 4 months prior to the previous contract expiration date");
			} catch (IOException e) {
				throw new ChaincodeException("The contract " + reference + " could not be deserialised from the blockchain", e);
			}

		}

	}

	public String sha1ForContract(RentContract contract) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			StringBuffer buffer = new StringBuffer();
			buffer.append(contract.getReference() + "-");
			buffer.append(contract.getLease_type() + "-");
			buffer.append(contract.getProperty_id() + "-");
			buffer.append(contract.getStreet_id() + "-");
			buffer.append(contract.getStreet_name() + "-");
			buffer.append(contract.getPost_code() + "-");
			buffer.append(contract.getDoor_number() + "-");
			buffer.append(contract.getHouse_name() + "-");
			buffer.append(contract.getEntrance_number() + "-");
			buffer.append(contract.getBlock() + "-");
			buffer.append(contract.getFlat_number() + "-");
			buffer.append(contract.getLocality_id() + "-");
			buffer.append(contract.getLocality_name() + "-");
			buffer.append(contract.getRegion_id() + "-");
			buffer.append(contract.getProperty_type() + "-");
			buffer.append(contract.getBedrooms() + "-");
			buffer.append(contract.getOccupants() + "-");
			buffer.append(contract.getFloor_area() + "-");
			buffer.append(contract.getRenewable() + "-");
			buffer.append(contract.getSublet() + "-");
			buffer.append(contract.getSublet_original_contract_reference() + "-");
			buffer.append(contract.getRenewal() + "-");
			buffer.append(contract.getRenewal_of_nonrenewable() + "-");
			buffer.append(contract.getRenewal_nonrenewable_original_contract_reference() + "-");
			buffer.append(contract.getRenewal_original_contract_reference() + "-");
			buffer.append(contract.getRenewal_preceeding_contract_reference() + "-");
			buffer.append(contract.getRegistration_date() + "-");
			buffer.append(contract.getCommencement_date() + "-");
			buffer.append(contract.getTermination_date() + "-");
			buffer.append(contract.getPayment_frequency() + "-");
			buffer.append(contract.getPayment_term() + "-");
			buffer.append(contract.getDeposit_amount() + "-");
			buffer.append(contract.getFeesPaymentDone() + "-");
			if (contract.getActions() != null)
				for (RentContractAction action : contract.getActions()) {
					buffer.append(action.getAction_id() + "-");
					buffer.append(action.getTime_stamp() + "-");
					buffer.append(action.getAction_type() + "-");
					buffer.append(action.getAction_value() + "-");
					buffer.append(action.getAction_note() + "-");
				}

			// I use own code to bytesToHex to avoid using a library just for this:
			return bytesToHex(md.digest(buffer.toString().getBytes(UTF_8))).toUpperCase();
		} catch (NoSuchAlgorithmException e) {
			throw new ChaincodeException("Can't find SHA-1 algorithm", e);
		}
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}
	// Transactional actions that edit the world state

	@Transaction
	public String registerContract(org.hyperledger.fabric.contract.Context ctx, String reference, String idString, String lease_typeString, Boolean renewable, String statusString, String property_id,
			String street_id, String street_name, String post_code, String door_number, String house_name, String entrance_number, String block, String flat_number, String locality_id,
			String locality_name, String region_id, String property_type, Integer bedrooms, Integer occupants, Double floor_area, Boolean renewal_of_nonrenewable, Boolean sublet,
			String sublet_original_contract_reference, String renewal_nonrenewable_original_contract_reference, String renewal_preceeding_contract_reference, String registration_date,
			String commencement_date, String termination_date, String payment_frequencyString, Long payment_term, Long deposit_amount, String note, Boolean feesPaymentDone) {

		this.registerContractValidateData(ctx, reference, idString, lease_typeString, renewable, statusString, property_id, street_id, street_name, post_code, door_number, house_name, entrance_number,
				block, flat_number, locality_id, locality_name, region_id, property_type, bedrooms, occupants, floor_area, renewal_of_nonrenewable, sublet, sublet_original_contract_reference,
				renewal_nonrenewable_original_contract_reference, renewal_preceeding_contract_reference, registration_date, commencement_date, termination_date, payment_frequencyString, payment_term,
				deposit_amount, note, feesPaymentDone);

		reference = reference.toUpperCase().trim();

		if (reference == null || reference.equals(""))
			throw new ChaincodeException("Contract reference needs to have a value");

		RentContract.LeaseType lease_type = RentContract.LeaseType.valueOf(lease_typeString);
		RentContract.Status status = RentContract.Status.valueOf(statusString);
		RentContract.PaymentFrequency payment_frequency = RentContract.PaymentFrequency.valueOf(payment_frequencyString);

		RentContract contract = new RentContract(reference, UUID.fromString(idString), lease_type, renewable, status, property_id, street_id, street_name, post_code, door_number, house_name,
				entrance_number, block, flat_number, locality_id, locality_name, region_id, property_type, bedrooms, occupants, floor_area, renewal_of_nonrenewable, sublet,
				sublet_original_contract_reference, renewal_nonrenewable_original_contract_reference, renewal_preceeding_contract_reference, registration_date, commencement_date, termination_date,
				payment_frequency, payment_term, deposit_amount, note, feesPaymentDone);
		contract.setHash(sha1ForContract(contract));

		// https://www.baeldung.com/jackson-object-mapper-tutorial
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			ctx.getStub().putState(reference, objectMapper.writeValueAsString(contract).getBytes(UTF_8));
			logger.info("Smart Contract reference " + reference + " saved in Fabric. Hash: " + contract.getHash());
		} catch (JsonProcessingException e) {
			throw new ChaincodeException("The contract " + reference + " could not be serialised as JSON", e);
		}
		return contract.getHash();
	}

	private enum Role {
		backend, registrant, system, lessor, lessee
	}

	@Transaction
	public void updateContractStatusValidateData(org.hyperledger.fabric.contract.Context ctx, String roleString, String reference, String action_id, String statusString, Long time_stamp,
			String action_note) {
		// try {
		UUID.fromString(action_id);
		RentContract.Status.valueOf(statusString);
		RentContract rentContract = getContract(ctx, reference);
		RentContract.Status currentContractStatus = rentContract.getStatus();

		// Basic sanity check validation to make sure that timestamp is after last timestamp:
		Long lastTimestamp = null;
		for (RentContractAction action : rentContract.getActions())
			lastTimestamp = action.getTime_stamp();
		if (lastTimestamp == null)
			throw new ChaincodeException("Contract " + reference + " has no actions or no action timestamp. This is an illegal situation so we can't continue");
		if (time_stamp < lastTimestamp)
			throw new ChaincodeException("Passed timestamp " + time_stamp + " is before previous action in contract " + reference + ": " + lastTimestamp);

		Role role = Role.valueOf(roleString.toLowerCase());
		RentContract.Status newStatus = RentContract.Status.valueOf(statusString);

		if (role.equals(Role.lessee)) {
			// 1. Shortlet can only be withdrawn by lessee after 1 month.
			if (rentContract.getLease_type().equals(RentContract.LeaseType.SL)) {
				LocalDate commencementPlusOneMonth = LocalDate.parse(rentContract.getCommencement_date()).plusMonths(1);
				if (!LocalDate.now().isAfter(commencementPlusOneMonth) && newStatus.equals(RentContract.Status.Terminated))
					throw new ChaincodeException("Contract " + reference + ": Shortlet can only be withdrawn by lessee after 1 month.");
			}

			// 2. Lessee can withdraw Long Let as described below:
			if (rentContract.getLease_type().equals(RentContract.LeaseType.LL)) {
				// a- If termination date is more than 6 months after commencement date and contract duration is less than 24 months.
				LocalDate commencement_date = LocalDate.parse(rentContract.getCommencement_date());
				LocalDate termination_date = LocalDate.parse(rentContract.getTermination_date());
				long monthsBetween = ChronoUnit.MONTHS.between(commencement_date, termination_date);
				if (!LocalDate.now().isAfter(commencement_date.plusMonths(6)) && newStatus.equals(RentContract.Status.Terminated) && monthsBetween < 24)
					throw new ChaincodeException(
							"Contract " + reference + ": Longlet can only be withdrawn by lessee after 6 months of commencement date and contract duration is less than 24 months.");
				// b- Termination date is more than 9 months after commencement date and the contract duration is for a period of a minimum of 24 months but less than 36 months.
				else if (!LocalDate.now().isAfter(commencement_date.plusMonths(9)) && newStatus.equals(RentContract.Status.Terminated) && monthsBetween >= 24 && monthsBetween < 36)
					throw new ChaincodeException("Contract " + reference
							+ ": Longlet can only be withdrawn by lessee after 9 months of commencement date and contract duration is a minimum of 24 months and less than 36 months.");
				// c- Termination date is more than 12 months from the commencement date and the contract duration is for a minimum of 36 months.
				else if (!LocalDate.now().isAfter(commencement_date.plusMonths(12)) && newStatus.equals(RentContract.Status.Terminated) && monthsBetween >= 36)
					throw new ChaincodeException(
							"Contract " + reference + ": Longlet can only be withdrawn by lessee after 12 months of commencement date and contract duration is a minimum of 36 months.");
			}

		}

		if (role.equals(Role.backend) && newStatus.equals(RentContract.Status.Terminated_by_HA)) {
			if (action_note == null || action_note.trim().isEmpty())
				throw new ChaincodeException("Contract " + reference + ": Setting contract status to Terminated by HA requires a note.");
			if (!currentContractStatus.equals(RentContract.Status.Active))
				throw new ChaincodeException(
						"Contract " + reference + ": Setting contract status to Terminated by HA requires active status of contract and found it '" + currentContractStatus + "'.");
		}

		if ((role.equals(Role.system) || role.equals(Role.backend)) && newStatus.equals(RentContract.Status.Compliance) && !currentContractStatus.equals(RentContract.Status.Active))
			throw new ChaincodeException("Contract " + reference + ": Moving contract status to Compliance requires active status of contract and found it '" + currentContractStatus + "'.");

	}

	@Transaction
	public void updateContractStatus(org.hyperledger.fabric.contract.Context ctx, String roleString, String reference, String action_idString, String statusString, Long time_stamp,
			String action_note) {
		updateContractStatusValidateData(ctx, roleString, reference, action_idString, statusString, time_stamp, action_note);
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			UUID action_id = UUID.fromString(action_idString);
			RentContract.Status newStatus = RentContract.Status.valueOf(statusString);
			RentContract rentContract = getContract(ctx, reference);
			RentContractAction action = new RentContractAction(action_id, time_stamp, "update_contract_status", newStatus.toString(), action_note);
			rentContract.setStatus(newStatus);
			rentContract.getActions().add(action);
			ctx.getStub().putState(reference, objectMapper.writeValueAsString(rentContract).getBytes(UTF_8));
		} catch (JsonProcessingException e) {
			throw new ChaincodeException("The contract " + reference + " could not be de-serialised from JSON", e);
		}
	}

	// Important: Returns a String containg the JSON of a Map.
	@Transaction
	public String getAllContractHashes(org.hyperledger.fabric.contract.Context ctx) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			Map<String, String> hashes = new HashMap<>();
			QueryResultsIterator<KeyValue> iterator = ctx.getStub().getStateByRange(lowerReference, higherReference);
			if (iterator == null)
				logger.warning("all contracts iterator null!!");
			else {
				for (KeyValue keyValue : iterator)
					try {
						RentContract rentContract = objectMapper.readValue(keyValue.getValue(), RentContract.class);
						// EXPURGANDA:
						// We recalculate hashes just in case there are old contracts.
						rentContract.setHash(sha1ForContract(rentContract));
						// EXPURGANDA END
						hashes.put(keyValue.getKey(), rentContract.getHash());
					} catch (Throwable e) {
						logger.log(Level.SEVERE, keyValue.getKey() + ": Error parsing JSON: " + new String(keyValue.getValue()), e);
					}
				iterator.close();
			}
			logger.info("Returning " + hashes.size() + " contract hashes");
			ObjectMapper mapper = new ObjectMapper();
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(hashes);
		} catch (JsonProcessingException e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		} catch (IOException e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		} catch (Exception e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		}
	}

	// Important: Very resource consuming
	@Transaction
	public String getAllContracts(org.hyperledger.fabric.contract.Context ctx) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			List<RentContract> allContracts = new ArrayList<>();
			QueryResultsIterator<KeyValue> iterator = ctx.getStub().getStateByRange(lowerReference, higherReference);
			if (iterator == null)
				logger.warning("all contracts iterator null!!");
			else {
				for (KeyValue keyValue : iterator)
					try {
						RentContract rentContract = objectMapper.readValue(keyValue.getValue(), RentContract.class);
						// EXPURGANDA:
						// We recalculate hashes just in case there are old contracts.
						rentContract.setHash(sha1ForContract(rentContract));
						// EXPURGANDA END
						allContracts.add(rentContract);
					} catch (Throwable e) {
						logger.log(Level.SEVERE, keyValue.getKey() + ": Error parsing JSON: " + new String(keyValue.getValue()), e);
					}
				iterator.close();
			}
			logger.info("Returning " + allContracts.size() + " contract hashes");
			ObjectMapper mapper = new ObjectMapper();
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(allContracts);
		} catch (JsonProcessingException e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		} catch (IOException e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		} catch (Exception e) {
			throw new ChaincodeException("Problem returning hashes for all rent contracts", e);
		}
	}

	// EXPURGANDA: Emergency method to be deleted. Allows to import all contracts into the ledger
	@Transaction
	public void importContracts(org.hyperledger.fabric.contract.Context ctx, String jsonArray) throws JsonMappingException, JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		List<RentContract> allContracts = mapper.readValue(jsonArray, new TypeReference<List<RentContract>>() {
		});
		for (RentContract contract : allContracts)
			try {
				ctx.getStub().putState(contract.getReference(), mapper.writeValueAsString(contract).getBytes(UTF_8));
				logger.info("Smart Contract reference " + contract.getReference() + " saved in Fabric. Hash: " + contract.getHash());
			} catch (JsonProcessingException e) {
				throw new ChaincodeException("The contract " + contract.getReference() + " could not be serialised as JSON", e);
			}

	}

	@Transaction
	public RentContract getContract(org.hyperledger.fabric.contract.Context ctx, String reference) {
		reference = reference.toUpperCase();
		byte[] contract = ctx.getStub().getState(reference);
		if (contract.length == 0)
			throw new ChaincodeException("The contract " + reference + " was not found ");

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			return objectMapper.readValue(contract, RentContract.class);
		} catch (JsonProcessingException e) {
			throw new ChaincodeException("The contract " + reference + " could not be de-serialised from JSON", e);
		} catch (IOException e) {
			throw new ChaincodeException("The contract " + reference + " could not be de-serialised from JSON", e);
		}
	}

	// When a renewable long let contract from 4rd month to expiration date, requires a new commencement date (previous + 1), new termination date conforming to long let validations, and new price,
	// new contract ID based on original contract + renew order number from, lease type set to LR and Then renew only (3rd month until the end). Original_contract_id will point to original contract
	// Id.
	@Transaction
	public void renewContractValidateData(org.hyperledger.fabric.contract.Context ctx, String reference, String commencement_date, String termination_date, Long payment_term, String note) {

		byte[] buffer = ctx.getStub().getState(reference);
		if (buffer == null || buffer.length == 0)
			throw new ChaincodeException("The contract " + reference + " does not exists. It can't be renewed.");
		// Validate dates:
		LocalDate.parse(commencement_date);
		LocalDate.parse(termination_date);
	}

	// When a renewable long let contract from 4rd month to expiration date, requires a new commencement date (previous + 1), new termination date conforming to long let validations, and new price,
	// new contract ID based on original contract + renew order number from, lease type set to LR and Then renew only (3rd month until the end). Original_contract_id will point to original contract
	// Id.
	@Transaction
	public void renewContract(org.hyperledger.fabric.contract.Context ctx, String reference, String commencement_date, String termination_date, Long payment_term, String note) {

		renewContractValidateData(ctx, reference, commencement_date, termination_date, payment_term, note);

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			RentContract rentalContract = objectMapper.readValue(ctx.getStub().getState(reference), RentContract.class);
			// We create the new action with old data and add it to the contract actions
			if (rentalContract.getActions() == null)
				rentalContract.setActions(new ArrayList<>());
			RentContractAction renewContractAction = new RentContractAction(UUID.randomUUID(), new Date().getTime(), "previous_contract_renewed",
					rentalContract.getCommencement_date() + "," + rentalContract.getTermination_date() + "," + rentalContract.getPayment_term(), "");
			rentalContract.getActions().add(renewContractAction);
			// Now we replace this data with the new one received for this contract
			rentalContract.setCommencement_date(commencement_date);
			rentalContract.setTermination_date(termination_date);
			rentalContract.setPayment_term(payment_term);
			rentalContract.setStatus(Status.Active);
			// This is from old design. This data is not used anymore. Kept here for reference:
			// rentalContract.setRenewal_original_contract_reference(reference);
			// rentalContract.setRenewal_preceeding_contract_reference(renewal_preceeding_contract_reference);// We should validate that this value is exactly the preceeding one, if any
			ctx.getStub().putState(reference, objectMapper.writeValueAsString(rentalContract).getBytes(UTF_8));

		} catch (JsonProcessingException e) {
			throw new ChaincodeException("The contract " + reference + " could not be de-serialised from JSON", e);
		} catch (IOException e) {
			throw new ChaincodeException("The contract " + reference + " could not be de-serialised from JSON", e);
		}

	}

	// Empty method for tests.
	@Transaction
	public void emptyMethod(org.hyperledger.fabric.contract.Context ctx) {
		logger.info("Empty method called");
		// }
	}

	@Transaction
	public void instantiate(org.hyperledger.fabric.contract.Context ctx) {
		// No implementation required with this example
		// It could be where data migration is performed, if necessary
		logger.info("No data migration to perform");
	}

}
