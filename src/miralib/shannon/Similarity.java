/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.shannon;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import java.util.HashMap;
import miralib.data.DataSlice2D;
import miralib.data.Variable;
import miralib.math.Numbers;
import miralib.utils.Project;

/**
 * Similarity score between two variables.
 *
 */

public class Similarity {
  protected static NormalDistribution normDist = new NormalDistribution();
  protected static HashMap<Double, Double> criticalValues = new HashMap<Double, Double>();
  
  static public float calculate(DataSlice2D slice, float pvalue, Project prefs) {
    Variable varx = slice.varx;
    Variable vary = slice.vary;

    if (varx.weight() || vary.weight() || (varx.subsample() && vary.subsample())) {
      // weight variables are not comparable, or subsample variables between 
      // each other
      return 0;
    } 
    
    Double area = new Double(1 - pvalue/2);
    Double cval = criticalValues.get(area);
    if (cval == null) {
      cval = normDist.inverseCumulativeProbability(area);      
      criticalValues.put(area,  cval);
    } 
    
    int count = slice.values.size();
    int[] res = BinOptimizer.calculate(slice, prefs.binAlgorithm);
    int binx = res[0];
    int biny = res[1];
    
    float ixy = MutualInformation.calculate(slice, binx, biny);
    boolean indep = false;
            
    if (Float.isNaN(ixy) || Float.isInfinite(ixy)) {
      indep = true;
    } else if (prefs.depTest == DependencyTest.NO_TEST || Numbers.equal(pvalue, 1)) {
      indep = ixy <= prefs.threshold;
    } else if (prefs.depTest == DependencyTest.SURROGATE_GAUSS) {
      indep = surrogateGauss(slice, ixy, prefs.binAlgorithm, prefs.surrCount, cval);            
    } else if (prefs.depTest == DependencyTest.SURROGATE_GENERAL) {      
      indep = surrogateGeneral(slice, ixy, prefs.binAlgorithm, pvalue);
    } else if (prefs.depTest == DependencyTest.GAMMA_TEST) {
      indep = gammaTest(ixy, binx, biny, count, pvalue);
    }
    
    if (indep) {
      return 0;
    } else {
      float hxy = JointEntropy.calculate(slice, binx, biny);      
      float w;
      if (Numbers.equal(0.0, hxy)) {
        w = 0;
      } else {
        w = Numbers.constrain(ixy / hxy, 0, 1);
        if (Float.isNaN(w)) w = 0;
      }      
      return w;
    }
  }  
  
  static protected boolean surrogateGauss(DataSlice2D slice, float ixy,
                                          int binAlgo, int scount, double cvalue) {
    int sbinx = 0;
    int sbiny = 0;         
    float meani = 0;
    float meaniSq = 0;
    float stdi = 0; 
    for (int i = 0; i < scount; i++) {
      DataSlice2D surrogate = slice.shuffle();          
      if (i == 0) {
        int[] sres = BinOptimizer.calculate(surrogate, binAlgo);
        sbinx = sres[0];
        sbiny = sres[1];
      }
      float smi = MutualInformation.calculate(surrogate, sbinx, sbiny);      
      meani += smi;
      meaniSq += smi * smi;
    }
    meani /= scount;
    meaniSq /= scount;
    stdi = (float)Math.sqrt(Math.max(0, meaniSq - meani * meani));      
    float zs = (ixy - meani) / stdi;
    if (Float.isNaN(zs) || Float.isInfinite(zs)) {
      return true;
    } else { 
      return -cvalue <= zs && zs <= cvalue;
    }    
  }
  
  static protected boolean surrogateGeneral(DataSlice2D slice, float ixy, 
                                            int binAlgo, float pvalue) {
    int sbinx = 0;
    int sbiny = 0;  
    float maxMI = 0;
    int numSurr = (int)(1/pvalue) - 1;
    for (int i = 0; i < numSurr; i++) {          
      DataSlice2D surrogate = slice.shuffle();
      if (i == 0) {
        int[] sres = BinOptimizer.calculate(surrogate, binAlgo);
        sbinx = sres[0];
        sbiny = sres[1];
      }
      maxMI = Math.max(maxMI, MutualInformation.calculate(surrogate, sbinx, sbiny));
    }
    return ixy < maxMI;    
  }
  
  static protected boolean gammaTest(float ixy, int binx, int biny, int count, float pvalue) {
    double shapePar = (binx - 1) * (biny - 1) / 2d;
    double scalePar = 1d / count;
    try { 
      GammaDistribution gammaDist = new GammaDistribution(shapePar, scalePar);
      double c = gammaDist.inverseCumulativeProbability(1 - pvalue);            
      return ixy <= c;
    } catch (Exception ex) {
      return true;
    }    
  }
}
