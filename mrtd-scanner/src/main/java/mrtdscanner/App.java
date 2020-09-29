package mrtdscanner;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.Util;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SODFile;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.*;
import org.jmrtd.protocol.AAResult;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

public class App {
    public static void main(String[] args) throws InterruptedException, CardException {
        boolean warned = false;
        while (true) {
            CardTerminal terminal = getCardTerminal();
            if (terminal == null) {
                if (!warned) {
                    System.err.println("No card terminal detected");
                    warned = true;
                }

                Thread.sleep(1000);
                continue;
            }

            if (warned) {
                System.err.println("Detected new terminal");
                warned = false;
            }

            try {
                if (!terminal.isCardPresent()) {
                    Thread.sleep(100);
                    continue;
                }

                attemptReadAndIssue(terminal);
            } catch (Exception e) {
                System.out.println("An unknown error occurred: " + e.toString());
                e.printStackTrace(System.out);
                Thread.sleep(500);
                continue;
            }
            System.err.println("Card read, sleeping");
            Thread.sleep(10000);
        }
    }

    private static PACEInfo getPACEInfo(CardAccessFile cardAccessFile) {
        for (SecurityInfo si : cardAccessFile.getSecurityInfos()) {
            if (si instanceof PACEInfo) {
                return (PACEInfo) si;
            }
        }

        return null;
    }

    private static byte[] filterNewlines(byte[] array) {
        byte[] tmp = new byte[array.length];

        int i = 0, j = 0;
        for (; i < array.length; ++i) {
            if (array[i] != 13) {
                tmp[j++] = array[i];
            }
        }

        return Arrays.copyOf(tmp, j);
    }

    private static void attemptReadAndIssue(CardTerminal terminal) throws CardException, CardServiceException, ParseException, IOException, FileNotFoundException, InterruptedException, GeneralSecurityException, OCRException {
        CardService cardService = CardService.getInstance(terminal);

        try {
            cardService.open();
        } catch (CardServiceException _e) {
            return;
        }

        byte[] mrzBuffer = OCR.read();

        if (mrzBuffer == null) {
            return;
        }

        System.out.println("Document MRZ found");

        mrzBuffer = filterNewlines(mrzBuffer);
        MRZInfo visualMRZ = new MRZInfo(new ByteArrayInputStream(mrzBuffer), mrzBuffer.length);

        System.out.println(visualMRZ);
        System.out.println(String.format("'%s' '%s' '%s'", visualMRZ.getDocumentNumber(), visualMRZ.getDateOfBirth(), visualMRZ.getDateOfExpiry()));

        BACKey passportBACKey = new BACKey(visualMRZ.getDocumentNumber(), visualMRZ.getDateOfBirth(), visualMRZ.getDateOfExpiry());

        PACEKeySpec passportPACEKey = PACEKeySpec.createMRZKey(passportBACKey);

        PassportService passportService = new PassportService(cardService, 16000, 16000, false, true);

        try {
            System.out.println("Sending");

            cardService.open();
            CardAccessFile caFile = new CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS));
            System.out.println("Read Card Access File");

            PACEInfo paceInfo = getPACEInfo(caFile);
            if (paceInfo == null) {
                throw new FileNotFoundException("No PACEInfo");
            }

            passportService.doPACE(passportPACEKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()), paceInfo.getParameterId());
            passportService.sendSelectApplet(true);

            System.out.println("PACE OK");

            // Read COM (LDS version & tags), eID DG1 (MRZ), DG2 (photo), DG15 (chipset public key), and SOD (per-block signatures).
            COMFile comFile = new COMFile(passportService.getInputStream(PassportService.EF_COM));
            DG1File dg1File = new DG1File(passportService.getInputStream(PassportService.EF_DG1));
            DG2File dg2File = new DG2File(passportService.getInputStream(PassportService.EF_DG2));
            DG15File dg15File = new DG15File(passportService.getInputStream(PassportService.EF_DG15));
            SODFile sodFile = new SODFile(passportService.getInputStream(PassportService.EF_SOD));

            System.out.println("Read all files");

            String sigAlg = "SHA256WithECDSA";
            String digAlg = Util.inferDigestAlgorithmFromSignatureAlgorithm(sigAlg);
            System.out.println("Performing AA with " + sigAlg + " / " + digAlg);
            byte[] challenge = {0x13, 0x37, 0x42, 0x04, 0x05, 0x06, 0x07, 0x08};
            PublicKey publicKey = dg15File.getPublicKey();
            AAResult aaResult = passportService.doAA(publicKey, digAlg, sigAlg, challenge);
            byte[] challengeResponse = aaResult.getResponse();

            IDExcerpt idExcerpt = new IDExcerpt();
            idExcerpt.challenge = challenge;
            idExcerpt.response = challengeResponse;

            idExcerpt.com = comFile.getEncoded();
            idExcerpt.dg1 = dg1File.getEncoded();
            idExcerpt.dg2 = dg2File.getEncoded();
            idExcerpt.dg15 = dg15File.getEncoded();
            idExcerpt.sod = sodFile.getEncoded();

            Gson gson = new GsonBuilder().disableHtmlEscaping().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            String jsonRepresentation = gson.toJson(idExcerpt);
            System.out.println(jsonRepresentation);
        } finally {
            cardService.close();
        }
    }

    private static CardTerminal getCardTerminal() throws CardException {
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();

        for (CardTerminal terminal : terminals) {
            if (terminal.getName().startsWith("Elyctis")) {
                return terminal;
            }
        }
        return null;
    }
}
