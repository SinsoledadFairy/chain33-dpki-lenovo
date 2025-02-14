package contract;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 部署和调用合约
 *
 */
public class DeployAndCall {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {

        // 生成以太坊地址和私钥
        // ECKeyPair keyPair = Keys.createEcKeyPair();
        // String privateKey = Numeric.toHexStringNoPrefix(keyPair.getPrivateKey());
        // String publicKey = Numeric.toHexStringNoPrefix(keyPair.getPublicKey());
        // Credentials credentials = Credentials.create(keyPair);
        // String address = credentials.getAddress();
        // System.out.println("address:" + address);
        // System.out.println("privateKey:" + privateKey);

        // 读取合约二进制代码
        String contractBin = new String(
                Files.readAllBytes(Paths.get(System.getProperty("user.dir"),
                        "/common/src/main/resource/", "contract.code")),
                StandardCharsets.UTF_8);

        // 部署智能合约
        Web3j web3 = Web3j.build(new HttpService("http://121.248.55.15:8545"));

        // 加载部署合约所需的凭证，用私钥
        Credentials credentials = Credentials
                .create("0x73e66f099144f820753aa3a5e131785b528081da572e16339fcd02de05de719e");
        Integer tokenId = 123456;
        // 获取chainid
        BigInteger chainId = web3.ethChainId().send().getChainId();

        // 部署合约
        String txHash = deployContract(web3, credentials, contractBin, chainId);

        // 等待交易确认
        Thread.sleep(10000);

        // 从交易回执行获取合约地址
        String contractAddress = getContractAddress(web3, txHash);
        // String contractAddress = "0x942267b6fe3c07f2317f3c4042aa9f25bbf1669b";
        // 调用合约
        callContract(web3, credentials, contractAddress, chainId, tokenId);

        // 等待交易确认
        Thread.sleep(10000);

        // 查询账户下NFT数量和uri等信息
        queryNFTInfo(web3, contractAddress, tokenId);
    }

    /**
     * 部署合约
     * 
//      * @return
//      * @throws IOException
//      * @throws ExecutionException
//      * @throws InterruptedException
//      */
    private static String deployContract(Web3j web3, Credentials credentials, String contractBin, BigInteger chainId)
            throws IOException, InterruptedException, ExecutionException {

        BigInteger nonce = web3
                .ethGetTransactionCount("0xab7F5238cbEfB02062241cf979e4994b656FB944", DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();

        EthGasPrice ethGasPrice = web3.ethGasPrice().sendAsync().get();
        BigInteger gasPrice = ethGasPrice.getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(5000000L);

        String encodedConstructor = FunctionEncoder
                .encodeConstructor(Arrays.asList(new org.web3j.abi.datatypes.Utf8String("init ERC1155")));
        BigInteger value = Convert.toWei("0", Convert.Unit.ETHER).toBigInteger();

        RawTransaction rawTransaction = RawTransaction.createContractTransaction(nonce, gasPrice, gasLimit, value,
                contractBin + encodedConstructor);

        byte[] signMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
        String hexValue = Numeric.toHexString(signMessage);

        EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(hexValue).sendAsync().get();
        Gson gson = new Gson();
        String tx = gson.toJson(ethSendTransaction);
        JsonObject jo = JsonParser.parseString(tx).getAsJsonObject();
        String txHash = jo.get("result").getAsString();
        System.out.println("部署合约交易hash值:" + txHash);
        return txHash;
    }

    /**
     * 根据交易hash获取合约地址
     * 
     * @param txHash
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private static String getContractAddress(Web3j web3, String txHash)
            throws InterruptedException, ExecutionException {
        // 根据hash获取合约地址
        EthGetTransactionReceipt transaction = web3.ethGetTransactionReceipt(txHash).sendAsync().get();
        Optional<TransactionReceipt> optionalTransaction = transaction.getTransactionReceipt();
        TransactionReceipt transactionInfo = new TransactionReceipt();
        if (optionalTransaction.isPresent()) {
            transactionInfo = optionalTransaction.get();
        }
        String contractAddress = transactionInfo.getContractAddress();
        System.out.println("合约地址:" + contractAddress + "\r\n");
        return contractAddress;
    }

    /**
     * 调用合约
     * 
     * @param web3
     * @param credentials
     * @param contractAddress
     * @param chainId
     * @param tokenId
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private static void callContract(Web3j web3, Credentials credentials, String contractAddress, BigInteger chainId, Integer tokenId)
            throws IOException, InterruptedException, ExecutionException {
        // 获取nonce，交易笔数
        BigInteger nonce = web3
                .ethGetTransactionCount("0xab7F5238cbEfB02062241cf979e4994b656FB944", DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();

        // 手续费
        EthGasPrice ethGasPrice = web3.ethGasPrice().sendAsync().get();
        BigInteger gasPrice = ethGasPrice.getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(500000L);

        // 合约参数
        int supply = 30;
        String uri = "http://121.248.55.15:9000/rayaxe.json";
        Function function = new Function("mint",
                Arrays.asList(new Uint256(tokenId), new Uint256(supply), new Utf8String(uri)), Collections.emptyList());

        // 创建合约交易对象
        String encodedFunction = FunctionEncoder.encode(function);
        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress,
                encodedFunction);

        // 签名Transaction，这里要对交易做签名
        byte[] signMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
        String hexValue = Numeric.toHexString(signMessage);
        // 发送交易
        EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(hexValue).sendAsync().get();
        Gson gson = new Gson();
        String tx = gson.toJson(ethSendTransaction);
        JsonObject jo = JsonParser.parseString(tx).getAsJsonObject();
        String txHash = jo.get("result").getAsString();
        System.out.println("调用合约交易hash值:" + txHash);

        // 等待交易确认
        Thread.sleep(20000);

        // 转账交易
        function = new Function("safeTransferFrom",
                Arrays.asList(new Address("0xab7F5238cbEfB02062241cf979e4994b656FB944"),
                        new Address("0x3f32619C6cd6E1a49382c686899Ec75D415F78ce"), new Uint256(tokenId), new Uint256(5), new org.web3j.abi.datatypes.DynamicBytes("0x00".getBytes())),
                Collections.emptyList());
        encodedFunction = FunctionEncoder.encode(function);
        nonce = web3
                .ethGetTransactionCount("0xab7F5238cbEfB02062241cf979e4994b656FB944", DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
        rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, encodedFunction);
        signMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
        hexValue = Numeric.toHexString(signMessage);
        // 发送交易
        ethSendTransaction = web3.ethSendRawTransaction(hexValue).sendAsync().get();
        tx = gson.toJson(ethSendTransaction);
        jo = JsonParser.parseString(tx).getAsJsonObject();
        txHash = jo.get("result").getAsString();
        System.out.println("转账交易hash值:" + txHash);

    }

    /**
     * 查询账户下NFT数量和uri等信息
     * 
     * @param web3
     * @param contractAddress
     * @param tokenId
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private static void queryNFTInfo(Web3j web3, String contractAddress, Integer tokenId)
            throws InterruptedException, ExecutionException {
        
        Function function = new Function("balanceOf",
                Arrays.asList(new Address("0xab7F5238cbEfB02062241cf979e4994b656FB944"), new Uint256(tokenId)),
                Collections.emptyList());
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = null;
        response = web3.ethCall(Transaction.createEthCallTransaction("0xab7F5238cbEfB02062241cf979e4994b656FB944",
                contractAddress, encodedFunction), DefaultBlockParameterName.LATEST).sendAsync().get();
        String value = response.getValue();
        System.out.println("账户下NFT数量:" + new BigInteger(value.substring(2), 16));
        System.out.println(tokenId);
        function = new Function("uri", Arrays.asList(new Uint256(tokenId)), Collections.emptyList());
        encodedFunction = FunctionEncoder.encode(function);
        response = web3.ethCall(Transaction.createEthCallTransaction("0xab7F5238cbEfB02062241cf979e4994b656FB944",
                contractAddress, encodedFunction), DefaultBlockParameterName.LATEST).sendAsync().get();
        value = response.getValue();
        System.out.println("账户下NFT uri:" + new String(Numeric.hexStringToByteArray(value.substring(2))));
    }
}