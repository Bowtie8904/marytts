package marytts.tools.voiceimport;

import java.io.*;
import java.util.*;

import marytts.cart.*;
import marytts.cart.LeafNode.FeatureVectorLeafNode;
import marytts.cart.LeafNode.FloatLeafNode;
import marytts.cart.LeafNode.IntAndFloatArrayLeafNode;
import marytts.cart.LeafNode.IntArrayLeafNode;
import marytts.cart.LeafNode.LeafType;
import marytts.cart.io.MaryCARTReader;
import marytts.cart.io.MaryCARTWriter;
import marytts.exceptions.MaryConfigurationException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.unitselection.data.Datagram;
import marytts.unitselection.data.FeatureFileReader;
import marytts.unitselection.data.MCepDatagram;
import marytts.unitselection.data.MCepTimelineReader;
import marytts.unitselection.data.UnitFileReader;
import marytts.util.MaryUtils;

class DiphoneCount implements Comparable<DiphoneCount>
{
    String diphone;
    int count;
    
    DiphoneCount(String diphone, int count) {
        this.diphone = diphone;
        this.count = count;
    }
    
    public int compareTo(DiphoneCount other) {
        if (count == other.count) return 0;
        if (count < other.count) return -1;
        return 1;
    }
    
    public boolean equals(Object dc)
    {
        if (!(dc instanceof DiphoneCount)) return false;
        DiphoneCount other = (DiphoneCount) dc;
        if (count == other.count) return true;
        return false;
    }
}


/**
 * Sanity checker for voicebuilding 
 * One test case : check no. of diphones in database and cart tree
 * @author sathish pammi
 *
 */
public class SanityChecker extends VoiceImportComponent {

    Map<String, Set<Integer>> diPhoneSet;
    Map<String, Set<Integer>> cartdiPhoneSet;
    
    public final String HALFFEATURES = "SanityChecker.halfPhoneFeatureFile";
    public final String CARTTREE = "SanityChecker.cartTreeFile";
    private DatabaseLayout db;    
    

    protected void setupHelp() {
        // TODO Auto-generated method stub
        props2Help = new TreeMap();
        props2Help.put(HALFFEATURES,"Half-phone feature file");
        props2Help.put(CARTTREE,"CART tree file (file generated by CARTBuilder)"); 
    }
    
    public SortedMap<String, String> getDefaultProps(DatabaseLayout db) {
        this.db = db;
        if (props == null){
            props = new TreeMap();
            
            props.put(HALFFEATURES,db.getProp(db.ROOTDIR)+File.separator
                    +"mary"+File.separator+"halfphoneFeatures.mry");
            props.put(CARTTREE,db.getProp(db.ROOTDIR)+File.separator
                         +"mary"+File.separator
                         +"cart.mry");
        }
        return props;
    }

    public final String getName() {
        return "SanityChecker";
    }


    public boolean compute() throws Exception {
        
        // One test case : check no. of diphones in database and cart tree 
        computeCoverage(getProp(HALFFEATURES));
        computeCARTCoverage(getProp(CARTTREE), getProp(HALFFEATURES));
        boolean success = printSanityCheckDetails(diPhoneSet, cartdiPhoneSet);
        
        return success;
    }


    
    
    public int getProgress() {
        return -1;
    }

    
   public void computeCoverage(String inFile) throws IOException, MaryConfigurationException
   {
        FeatureFileReader ffr = FeatureFileReader.getFeatureFileReader(inFile);
        FeatureVector[] fVCopy = ffr.getCopyOfFeatureVectors();
        FeatureDefinition feaDef = ffr.getFeatureDefinition();
        //int cUnitIdx; // current unit index
        System.out.println("Features used to build voice : ");
        System.out.println(feaDef.getFeatureNames());
        int pIdx = feaDef.getFeatureIndex("phone");
        int hpIdx = feaDef.getFeatureIndex("halfphone_unitname");
        int lrIdx = feaDef.getFeatureIndex("halfphone_lr");
        diPhoneSet = new HashMap<String, Set<Integer>>();
        
        for(int i=0; i < fVCopy.length; i++ ){
            FeatureVector fv = fVCopy[i];
            
            int cUnitIdx = fv.getUnitIndex();
            if(cUnitIdx <= 0) continue;
            FeatureVector prevFV = fVCopy[i-1]; 
            if(fv.getFeatureAsString(lrIdx, feaDef).equals("R")){
                continue;
            }
            
            String prevStr = prevFV.getFeatureAsString(hpIdx, feaDef);
            String cStr = fv.getFeatureAsString(hpIdx, feaDef);
            String prevPh =  prevFV.getFeatureAsString(pIdx, feaDef);
            String cPh =  fv.getFeatureAsString(pIdx, feaDef);
            String diPhone = prevPh +"_"+ cPh;
            
            if(diPhoneSet.containsKey(diPhone)){
                Set<Integer> setList = (Set<Integer>) diPhoneSet.get(diPhone);
                setList.add(cUnitIdx); 
                diPhoneSet.put(diPhone, setList);
                }
                else {
                    Set<Integer> setList = new HashSet<Integer>();
                    setList.add(cUnitIdx);
                    diPhoneSet.put(diPhone, setList);
                }
        }
    }
    
    
   private void computeCARTCoverage(String cartFile, String halfPhones) throws Exception{
       
       //ClassificationTree cart = new ClassificationTree();
       CART cart = new CART();
       MaryCARTReader mCart = new MaryCARTReader();
       cart = mCart.load(cartFile);
       
       FeatureFileReader ffr = FeatureFileReader.getFeatureFileReader(halfPhones);
       FeatureDefinition feaDef = ffr.getFeatureDefinition();
       cartdiPhoneSet = new HashMap<String, Set<Integer>>();
       int pIdx = feaDef.getFeatureIndex("phone");
       int hpIdx = feaDef.getFeatureIndex("halfphone_unitname");
       int lrIdx = feaDef.getFeatureIndex("halfphone_lr");
       
       ArrayList<Integer> listHalfUnits = new ArrayList<Integer>();
       for (LeafNode leaf : cart.getLeafNodes()) {
           int[] data = (int[])leaf.getAllData();
           //System.out.println("Data SIZE : "+data.length);
           for (int i=0; i<data.length; i++) {
           //    System.out.println("Data Val: "+data[i]);
               listHalfUnits.add(data[i]);
           }
       }
       ArrayList<Integer> refList = new  ArrayList<Integer>();
       refList = listHalfUnits;
       //PrintWriter pw=new PrintWriter(new FileWriter("/home/sathish/Work/test/t2"));
       
       for ( Iterator<Integer> it = listHalfUnits.iterator(); it.hasNext(); ){
           int unitIndex = ((Integer) it.next()).intValue();
           if(unitIndex <= 0) continue;
           int size = ffr.getNumberOfUnits();
           if(unitIndex >= size){
               System.err.println("Warning: Unit index "+unitIndex+" is greater that size "+size);
               continue;
           }
           FeatureVector fv = ffr.getFeatureVector(unitIndex);
           
           if(fv.getFeatureAsString(lrIdx, feaDef).equals("R")){
               continue;
           }
           
           if(!containsUnitIndex(refList, unitIndex-1)){
               continue;
           }
           
           FeatureVector prevFV = ffr.getFeatureVector(unitIndex-1); 
           String prevStr = prevFV.getFeatureAsString(hpIdx, feaDef);
           String cStr = fv.getFeatureAsString(hpIdx, feaDef);
           String prevPh =  prevFV.getFeatureAsString(pIdx, feaDef);
           String cPh =  fv.getFeatureAsString(pIdx, feaDef);
           String diPhone = prevPh +"_"+ cPh;
           //System.out.println(unitIndex);
           
           if(cartdiPhoneSet.containsKey(diPhone)){
               Set<Integer> setList = (Set<Integer>) cartdiPhoneSet.get(diPhone);
               setList.add(unitIndex); 
               cartdiPhoneSet.put(diPhone, setList);
               }
               else {
                   Set<Integer> setList = new HashSet<Integer>();
                   setList.add(unitIndex);
                   cartdiPhoneSet.put(diPhone, setList);
               }
       }
   }
   
    private boolean containsUnitIndex(ArrayList<Integer> refList, int uIndex){
        
        for ( Iterator<Integer> it = refList.iterator(); it.hasNext(); ){
            int listVal = ((Integer) it.next()).intValue();
            if(listVal == uIndex){
                return true;
            }
        }
        return false;
    }
    //<String, Set<Integer>>
    private void printData(Map<String, Set<Integer>> mp, boolean printAll){

        for ( Iterator it = mp.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry e = (Map.Entry) it.next();
            Set<Integer> arr = (Set<Integer>) e.getValue();
            String keyVal = (String) e.getKey();
            int intVal = arr.size();
            //System.out.println("Diphone: "+ keyVal+ " Count: "+intVal);
            System.out.println(keyVal+" "+intVal);
            if(printAll){
                for ( Iterator sit = arr.iterator(); sit.hasNext(); ){
                    System.out.print( "   "+ sit.next());
                }
                System.out.println();
            }
        }
    }
    
    
 
    private void printDataToFile(Map mp, Map cartMap, String fileName) throws Exception{
        
        PrintWriter pw=new PrintWriter(new FileWriter(fileName));
        
        DiphoneCount[] mpSorted = new DiphoneCount[mp.entrySet().size()];
        
        int i=0;
        for ( Iterator it = mp.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry e = (Map.Entry) it.next();
            Set arr = (Set) e.getValue();
            String keyVal = (String) e.getKey();
            mpSorted[i] = new DiphoneCount(keyVal, arr.size());
            i++;
        }
        Arrays.sort(mpSorted);
        
        for (i=0; i<mpSorted.length; i++) {
            String diphone = mpSorted[i].diphone;
            int mpCount = mpSorted[i].count;
            int cartSetSize = 0;
            if(cartMap.containsKey(diphone)){
                Set cartSet = (Set)cartMap.get(diphone);
                cartSetSize = cartSet.size();
            }
            System.out.println(diphone+" "+mpCount+" "+cartSetSize);
            pw.println(diphone+" "+mpCount+" "+cartSetSize);
        }
        pw.flush();
        pw.close();
    }
    
    private boolean printSanityCheckDetails(Map mp, Map cartMap) throws Exception{
        
        DiphoneCount[] mpSorted = new DiphoneCount[mp.entrySet().size()];
        
        int i=0;
        for ( Iterator it = mp.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry e = (Map.Entry) it.next();
            Set arr = (Set) e.getValue();
            String keyVal = (String) e.getKey();
            mpSorted[i] = new DiphoneCount(keyVal, arr.size());
            i++;
        }
        Arrays.sort(mpSorted);
        
        // First print all good things
        for (i=0; i<mpSorted.length; i++) {
            String diphone = mpSorted[i].diphone;
            int mpCount = mpSorted[i].count;
            int cartSetSize = 0;
            if(cartMap.containsKey(diphone)){
                Set cartSet = (Set)cartMap.get(diphone);
                cartSetSize = cartSet.size();
            }
            if(mpCount == cartSetSize){
                System.out.println("For diphone: "+diphone+" ; No. of diphones in database: "+mpCount+" ;  No. of diphones in CART tree:"+cartSetSize+ " --> OK. ");
            }
        }
        
        int failCount = 0;
        // Next print all bad things
        for (i=0; i<mpSorted.length; i++) {
            String diphone = mpSorted[i].diphone;
            int mpCount = mpSorted[i].count;
            int cartSetSize = 0;
            if(cartMap.containsKey(diphone)){
                Set cartSet = (Set)cartMap.get(diphone);
                cartSetSize = cartSet.size();
            }
            if(mpCount != cartSetSize){
                System.out.println("WARNING :: For diphone: "+diphone+" ; No. of diphones in database: "+mpCount+" ;  No. of diphones in CART tree:"+cartSetSize+ " --> NOT OK. ");
                failCount++;
            }
        }
        
        if(failCount > 0){
            System.out.println("ERROR: Failed sanity check for "+failCount+" diphone units");
            return false;
        }
        else return true;
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {

        String halfPhonesFile = "cmu-slt-arctic/mary/halfphoneFeatures.mry";
        String cartFile = "cmu_us_slt_arctic/mary/cart.mry";
        
        SanityChecker dc = new SanityChecker();
        dc.computeCoverage(halfPhonesFile);
        dc.computeCARTCoverage(cartFile, halfPhonesFile);
        dc.printSanityCheckDetails(dc.diPhoneSet, dc.cartdiPhoneSet);
        
    }


    

}

