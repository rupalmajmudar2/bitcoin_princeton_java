import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Vector;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
	private UTXOPool _utxoPool;
    public TxHandler(UTXOPool utxoPool) {
    	_utxoPool= new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        //(1) all outputs claimed by {@code tx} are in the current UTXO pool
    	//      "Actually it means: All outputs which claimed by {@code tx.Input} are in the current UTXO pool."
    	ArrayList<Transaction.Input> inputs= tx.getInputs();
    	ArrayList<Transaction.Output> outputs= tx.getOutputs();
    	for (int i = 0; i < inputs.size(); i++) {
    		Transaction.Input in = (Transaction.Input) inputs.get(i);
    		UTXO uu= new UTXO(in.prevTxHash, in.outputIndex);
    		if (_utxoPool.contains(uu)) {
    			//System.out.println("Gotit!");
    		}
    		else  {
    			System.out.println("NOT Got it : " + in.outputIndex);
    			return false;
    		}
    	}
    	
    	//(2) the signatures on each input of {@code tx} are valid, 
    	//ArrayList<Transaction.Output> outputs= tx.getOutputs();
    	for (int i = 0; i < inputs.size(); i++) {
    		Transaction.Input in = (Transaction.Input) inputs.get(i);
    		
    		byte[] txn_signature= in.signature;
    		//specify a public key of Scrooge (pk_scrooge). How to get it?
    		//Use the UTXOpool. The inputs of this TX must be in the UTXOpool. So find the UTXO in that pool.
    		byte[] message= tx.getRawDataToSign(i); //No not in.outputIndex);
    		if (message == null) {
    			//incorrect outputIndex
    			return false;
    		}
    		
    		/**
    		 * No! The tx.outputs[0] will give the public key of THIS recipient
    		 * We need the public key of the sender
    		 *
    		Transaction.Output output= outputs.get(0);
    		PublicKey pubKey= output.address;
    		
    		To get that, find the utxo in the pool and get its output!
    		*/
    		UTXO ut= new UTXO(in.prevTxHash, in.outputIndex);
    		Transaction.Output txOut= _utxoPool.getTxOutput(ut);
    		PublicKey pubKey= txOut.address;
    		//System.out.println("Txn#" + tx.hashCode() + " Data= " + message.hashCode() + " TxnValue=" + txOut.value + " Txn outputIndex=" + in.outputIndex + " Pub key is: " + pubKey.hashCode());
    		boolean okSign= Crypto.verifySignature(pubKey, message, txn_signature);
    		System.out.print("<br>Signature is ok? " + okSign);
    		if (!okSign) {
    			System.out.print(" : Signature NOT ok! ERROR!");
    			return false;
    		}
    		else {
    			//System.out.print(" : Signature ok!");
    		}
    	}
    	
    	//(3) no UTXO is claimed multiple times by {@code tx},
    	//See each of the outputIndexes. Each should come only once.
    	/*
    	 * "It might be easiest to read UTXO as "unspent coin". And the UTXOpool is a list of all the unspent coins.
			You should be checking that the TX isn't trying to spend the same coin twice."
    	 */
    	Vector inputValues= new Vector();
       	for (int i = 0; i < inputs.size(); i++) {
    		Transaction.Input in = (Transaction.Input) inputs.get(i);
    		UTXO ut= new UTXO(in.prevTxHash, in.outputIndex);
    		Transaction.Output txOut= _utxoPool.getTxOutput(ut);
    		System.out.println("Step#3: Txn#" + tx.hashCode() + " Index=" + in.outputIndex + " TxnValue=" + txOut.value);
    		if (inputValues.contains(txOut.value)) {
    			System.out.println("Step#3: Found duplicate value= " + txOut.value + " - Error! ");
    			return false;
    		}
    		inputValues.add(txOut.value);
       	}
    		
    	//(4) Values
    	//(4) all of {@code tx}s output values are non-negative, and
        // (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
        //     values; and false otherwise.
    	double totalInputValue= 0.0;
       	for (int i = 0; i < inputs.size(); i++) {
    		Transaction.Input in = (Transaction.Input) inputs.get(i);
    		UTXO ut= new UTXO(in.prevTxHash, in.outputIndex);
    		Transaction.Output txOut= _utxoPool.getTxOutput(ut);
    		System.out.println("Txn#" + tx.hashCode() + " Index=" + in.outputIndex + " TxnValue=" + txOut.value);
    		totalInputValue= totalInputValue + txOut.value;
       	}
       	System.out.println("Total Input Value=" + totalInputValue);		
    				
    	double totalOutputValue= 0.0;
       	for (int i = 0; i < outputs.size(); i++) {
    		Transaction.Output out = (Transaction.Output) outputs.get(i);
    		int outAddr= out.address.hashCode();
    		double outVal= out.value;
    		System.out.println("Txn#" + tx.hashCode() + " Addr=" + outAddr + " TxnValue=" + outVal);
    		if (outVal < 0.0) {
    			System.out.println("Negative OutVal: " + outVal + ". Error!");
    			return false;
    		}
    		totalOutputValue= totalOutputValue + outVal;
       	}
       	System.out.println("Total Output Value=" + totalOutputValue);	
       	
       	if (totalOutputValue > totalInputValue) {
			System.out.println("Total OutValue " + totalOutputValue + " > Total InValue " + totalInputValue + ". Error!");
			return false;
       	}
       	
    	return true;
    }
    
    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	Vector<Transaction> acceptedTxs= new Vector<Transaction>();
    	for (int i=0; i < possibleTxs.length; i++) {
    		Transaction txToCheck= possibleTxs[i];
    		if (isValidTx(txToCheck)) {
    			acceptedTxs.addElement(txToCheck);
    			
    			//updating the current UTXO pool as appropriate.
    			//There are 3 outputs all to one publickey
    			ArrayList outputs= txToCheck.getOutputs();
    			for (int j=0; j < outputs.size(); j++) {
    				Transaction.Output o= (Transaction.Output) outputs.get(j);
    				UTXO utxo = new UTXO(txToCheck.getHash(),j);
    				_utxoPool.addUTXO(utxo, o);
    				System.out.println("TxHandler: addUTXO Value=" + o.value + " at index=" + j + " to txn=" + txToCheck.hashCode());
    			}
    			
    	    	//TBD: We've added the new unclaimed txns (UTXOs) - we should remove the earlier ones!
    			//_utxoPool.removeUTXO(utxo);
    			ArrayList inputs= txToCheck.getInputs();
    			for (int j=0; j < inputs.size(); j++) {
    				Transaction.Input ii= (Transaction.Input) inputs.get(j);
    				UTXO ut= new UTXO(ii.prevTxHash, ii.outputIndex);
    				Transaction.Output txOut= _utxoPool.getTxOutput(ut);
    				_utxoPool.removeUTXO(ut);
    				System.out.println("TxHandler: removedUTXO Value=" + txOut.value + " at index=" + ii.outputIndex + " from txn=" + txToCheck.hashCode());
    			}
    			
    		}
    	}
    	

    	
    	Transaction[] acceptedTxsArray= new Transaction[acceptedTxs.size()];
    	Transaction[] ret= acceptedTxs.toArray(acceptedTxsArray);
    	return ret;
    }

}
