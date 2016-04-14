package wt.alignment;

import java.util.ArrayList;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

public class Median {
	public static FloatType median(Img<FloatType> image, ArrayList< Coordinates > indexList) {
		if (indexList.size() == 0)
			return null;
		Quicksort qSortTmp = new Quicksort(image, indexList);
		ArrayList< Integer > sortedIndex = qSortTmp.qsort();
		
		FloatType med = new FloatType();
		if (indexList.size() % 2 == 0){
			med.set(qSortTmp.accessor(sortedIndex.get(indexList.size() / 2)));
		} else {
			int i = (int) Math.ceil((double)(indexList.size()) / 2);
			med.set( ( qSortTmp.accessor(sortedIndex.get(i  ))  + 
					   qSortTmp.accessor(sortedIndex.get(i+1))   ) /2 );
			
		}
		System.out.println( "median : " + med.getRealFloat() );
		return med;
	}
	
}
