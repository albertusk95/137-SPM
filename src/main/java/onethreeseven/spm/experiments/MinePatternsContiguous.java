package onethreeseven.spm.experiments;

import ca.pfv.spmf.algorithms.sequentialpatterns.spam.AlgoTKS;
import onethreeseven.common.util.FileUtil;
import onethreeseven.spm.algorithm.*;
import onethreeseven.spm.data.SPMFParser;
import java.io.File;
import java.io.IOException;

/**
 * Use {@link onethreeseven.spm.algorithm.ProtoMiner} to mine SPMF database.
 * @author Luke Bermingham
 */
public class MinePatternsContiguous {

    private static final String filename = "tdrive";
    private static final File inFile = new File(FileUtil.makeAppDir("spmf-files"), filename + ".txt");
    private static final int topk = 797;

    private enum SPClosure {
        ALL, CLOSED, MAX, DISTINCT, TOPK
    }

    private static File makeOutFile(SPClosure closure, int minSupAbs, double maxRedundancy){

        int redund = (int) (maxRedundancy * 100);

        String outFileName = filename + "_" + closure.name() +
                ((closure == SPClosure.TOPK && topk > 0) ? "_" + topk : "_minsup_" + minSupAbs) +
                (closure == SPClosure.DISTINCT ? "redund_" + redund : "") +  ".txt";
        return new File(FileUtil.makeAppDir("contig_patterns/" + filename), outFileName);
    }

    public static void main(String[] args) throws IOException {

        System.out.println("Loading spmf file");
        SPMFParser parser = new SPMFParser();
        System.out.println("Closure, Support, Rel Sup, #Sequences, #Items, Average Sequence Length, #Distinct items, Redundancy(%), Running Time(ms), Compression(%), Lossiness (%)");

        int[][] seqDB = parser.parseSequences(inFile);

        int[] sups = {1405};
        double[] maxRedunds = {0};
        SPClosure[] closures = {SPClosure.DISTINCT};

        //do mining with this sup and this closure
        for (int minSupAbs : sups) {
            for (SPClosure closure : closures) {
                if(closure == SPClosure.DISTINCT){
                    for (double maxRedund : maxRedunds) {
                        doMining(seqDB, closure, minSupAbs, maxRedund);
                    }
                }else{
                    doMining(seqDB, closure, minSupAbs, 0);
                }
            }
        }

        //doMining(seqDB, selectedPatternClosure, minSupAbs);
    }

    private static void doMining(int[][] seqDB, SPClosure selectedPatternClosure, int minSupAbs, double maxRedundancy) throws IOException {

        File outFile = makeOutFile(selectedPatternClosure, minSupAbs, maxRedundancy);
        long startTime = System.currentTimeMillis();

        switch (selectedPatternClosure){
            case ALL:
                System.out.print("All, ");
                new ACSpan().run(seqDB, minSupAbs, outFile);
                break;
            case CLOSED:
                System.out.print("Closed, ");
                new CCSpan().run(seqDB, minSupAbs, outFile);
                break;
            case MAX:
                System.out.print("Max, ");
                new MCSpan().run(seqDB, minSupAbs, outFile);
                break;
            case DISTINCT:
                System.out.print("Distinct " + maxRedundancy + ", ");
                File allPatternsFile = makeOutFile(SPClosure.ALL, minSupAbs, maxRedundancy);
                if(!allPatternsFile.exists()){
                    System.out.println("Need output to mine, run AC-SPAN first.");
                }else{
                    new DCSpan().run(seqDB, new SPMFParser().parsePatterns(allPatternsFile), maxRedundancy, outFile);
                }
                break;
            case TOPK:
                System.out.print("topk-" + topk);
                AlgoTKS algo = new AlgoTKS();
                algo.setMaxGap(1);
                algo.runAlgorithm(inFile.getAbsolutePath(), outFile.getAbsolutePath(), topk);
                algo.writeResultTofile(outFile.getAbsolutePath());
                break;
        }

        long runningTime = System.currentTimeMillis() - startTime;

        System.out.print(minSupAbs + ", " + (double)minSupAbs/seqDB.length + ", ");

        SequenceDbStatsCalculator stats = new SequenceDbStatsCalculator();
        stats.calculate(new SPMFParser().parseSequences(outFile));

        System.out.print(
                stats.getTotalSequences() + ", " +
                        stats.getTotalItems() + ", " +
                        stats.getAvgSequenceLength() + ", " +
                        stats.getnDistinctItems() + ", " +
                        stats.getRedundancy() + ", " +
                        runningTime + ", ");

        //output compression and lossiness
        if(selectedPatternClosure == SPClosure.ALL){
            System.out.print("0, 0");
        }else{
            File allPatternsFile = makeOutFile(SPClosure.ALL, minSupAbs, maxRedundancy);
            if(allPatternsFile.exists()){
                //do compression
                int allItemsSubset = stats.getTotalItems();
                stats.calculate(new SPMFParser().parseSequences(allPatternsFile));
                int allItems = stats.getTotalItems();
                double compression = 1 - ((double)allItemsSubset)/allItems;
                System.out.print(compression + ", ");
                //do lossiness
                if(selectedPatternClosure == SPClosure.DISTINCT){
                    double lossiness = new PatternsLossinessCalculator().run(outFile, allPatternsFile);
                    System.out.print(lossiness);
                }
                else{
                    System.out.print(0);
                }
            }else{
                System.out.print("Run AC-SPAN");
            }
        }
        System.out.println(" ");

    }

}
