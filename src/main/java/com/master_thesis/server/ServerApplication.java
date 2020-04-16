package com.master_thesis.server;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.master_thesis.server.data.*;
import com.master_thesis.server.util.HttpAdapter;
import com.master_thesis.server.util.PublicParameters;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
@RestController
@RequestMapping(value = "/api")
public class ServerApplication {
    private static final Logger log = (Logger) LoggerFactory.getLogger(ServerApplication.class);
    private final URI verifier = URI.create("http://localhost:3000/api/server/");
    private PublicParameters publicParameters;
    private HomomorphicHash homomorphicHash;
    private RSAThreshold rsaThreshold;
    private LinearSignature linearSignature;
    private NonceDistribution nonceDistribution;
    private HttpAdapter httpAdapter;
    private Buffer buffer;
    private int serverID;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ServerApplication(PublicParameters publicParameters, HomomorphicHash homomorphicHash, RSAThreshold rsaThreshold, LinearSignature linearSignature, NonceDistribution nonceDistribution, HttpAdapter httpAdapter) {
        this.publicParameters = publicParameters;
        this.homomorphicHash = homomorphicHash;
        this.rsaThreshold = rsaThreshold;
        this.linearSignature = linearSignature;
        this.nonceDistribution = nonceDistribution;
        this.httpAdapter = httpAdapter;
        this.buffer = new Buffer(publicParameters);
        this.serverID = publicParameters.getServerID();
        new Thread(() -> {
            boolean running = true;
            while (running) {
                System.out.println("Enter r to re-register server, [l]ist servers");
                Scanner input = new Scanner(System.in);
                switch (input.next()) {
                    case "r":
                        publicParameters.reRegisterServer();
                        serverID = publicParameters.getServerID();
                        break;
                    case "l":
                        System.out.println(publicParameters.getServerList());

                }
            }

        }).start();
    }

    @SneakyThrows
    @PostMapping(value = "/hash-data")
    void receiveHashShare(@RequestBody HashIncomingData dataFromClient) {
        log.debug("Received share: {} ", objectMapper.writeValueAsString(dataFromClient));
        buffer.putClientShare(dataFromClient);
        if (buffer.canCompute(dataFromClient.getSubstationID(), dataFromClient.getFid())) {
            new Thread(() -> performComputations(dataFromClient.getSubstationID(), dataFromClient.getFid())).start();
        }
    }

    @PostMapping(value = "/rsa-data")
    void receiveRSAShare(@RequestBody RSAIncomingData dataFromClient) {
        log.debug("Received share: {} ", dataFromClient);
        buffer.putClientShare(dataFromClient);
        if (buffer.canCompute(dataFromClient.getSubstationID(), dataFromClient.getFid())) {
            new Thread(() -> performComputations(dataFromClient.getSubstationID(), dataFromClient.getFid())).start();
        }
    }

    @PostMapping(value = "/linear-data")
    void receiveLinearShare(@RequestBody LinearIncomingData dataFromClient) {
        log.debug("Received share: {} ", dataFromClient);
        buffer.putClientShare(dataFromClient);
        if (buffer.canCompute(dataFromClient.getSubstationID(), dataFromClient.getFid())) {
            new Thread(() -> performComputations(dataFromClient.getSubstationID(), dataFromClient.getFid())).start();
        }
    }

    @PostMapping(value = "/nonce-data")
    void receiveNonceDistributionShare(@RequestBody NonceIncomingData dataFromClient) {
        log.debug("Received share: {} ", dataFromClient);
        buffer.putClientShare(dataFromClient);
        if (buffer.canCompute(dataFromClient.getSubstationID(), dataFromClient.getFid())) {
            new Thread(() -> performComputations(dataFromClient.getSubstationID(), dataFromClient.getFid())).start();
        }
    }

    private void performComputations(int substationID, int fid) {
        log.info("=== Computing partial information for Substation {} fid {} === ", substationID, fid);
        Buffer.Fid fidData = buffer.getFid(substationID, fid);

//      Compute all common operations
        BigInteger fieldBase = publicParameters.getFieldBase(substationID);
        BigInteger generator = publicParameters.getGenerator(substationID);

        Construction construction = fidData.getConstruction();

//      Compute partial result and proof for HomomorphicHash construction
        if (construction.equals(Construction.HASH)) {
            List<HashIncomingData> computationData = fidData.values().stream().map(v -> (HashIncomingData) v).collect(Collectors.toList());
            List<BigInteger> shares = computationData.stream().map(HashIncomingData::getSecretShare).collect(Collectors.toList());
            BigInteger partialProof = homomorphicHash.homomorphicPartialProof(shares, fieldBase, generator);
            BigInteger partialResult = homomorphicHash.partialEval(shares, fieldBase);
            httpAdapter.sendWithTimeout(verifier.resolve(Construction.HASH.getEndpoint()), new HashOutgoingData(substationID, fid, serverID, partialResult, partialProof), 3000);
        }

//      Compute partial result and proof for RSAThreshold construction
        if (construction.equals(Construction.RSA)) {
            List<RSAIncomingData> computationData = fidData.values().stream().map(v -> (RSAIncomingData) v).collect(Collectors.toList());
            List<BigInteger> shares = computationData.stream().map(RSAIncomingData::getShare).collect(Collectors.toList());
            BigInteger partialResult = rsaThreshold.partialEval(shares, fieldBase);
            Map<Integer, RSAOutgoingData.ProofData> partialProofs = rsaThreshold.rsaPartialProof(computationData);
            httpAdapter.sendWithTimeout(verifier.resolve(Construction.RSA.getEndpoint()), new RSAOutgoingData(substationID, fid, serverID, partialResult, partialProofs), 3000);
        }

//      Compute partial result for LinearSignature construction
        if (construction.equals(Construction.LINEAR)) {
            List<LinearIncomingData> computationData = fidData.values().stream().map(v -> (LinearIncomingData) v).collect(Collectors.toList());
            List<BigInteger> shares = computationData.stream().map(LinearIncomingData::getSecretShare).collect(Collectors.toList());
            BigInteger partialResult = linearSignature.partialEval(shares, fieldBase);
            httpAdapter.sendWithTimeout(verifier.resolve(Construction.LINEAR.getEndpoint()), new LinearOutgoingData(substationID, fid, serverID, partialResult), 3000);
        }

//      Compute partial result for NonceDistribution construction
        if (construction.equals(Construction.NONCE)) {
            List<NonceIncomingData> computationData = fidData.values().stream().map(v -> (NonceIncomingData) v).collect(Collectors.toList());
            List<BigInteger> secretShares = computationData.stream().map(NonceIncomingData::getSecretShare).collect(Collectors.toList());
            List<BigInteger> nonceShares = computationData.stream().map(NonceIncomingData::getNonceShare).collect(Collectors.toList());
            BigInteger partialProof = nonceDistribution.partialProof(secretShares, fieldBase, generator);
            BigInteger partialNonce = nonceDistribution.partialNonce(nonceShares);
            BigInteger partialResult = nonceDistribution.partialEval(secretShares);
            httpAdapter.sendWithTimeout(verifier.resolve(Construction.NONCE.getEndpoint()),
                    new NonceOutgoingData(substationID, fid, serverID, partialResult, partialProof, partialNonce), 3000);
        }
    }


    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

}
