package wt.alignment;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.RandomAccess;
import java.util.ArrayList;

public class Quicksort
{
	Img<FloatType> image;
	ArrayList< Coordinates > positionList;
	ArrayList<Integer> indexList = null;
	RandomAccess< FloatType > r;
	public Quicksort(Img<FloatType> _image, ArrayList< Coordinates > _positionList) {
		positionList = _positionList;
		image = _image;
		r = image.randomAccess();
		this.indexList = new ArrayList<Integer>(positionList.size());
		for (int i = 0; i<positionList.size() ; i++) {
			indexList.add(i,i);
		}
	}
	
	public ArrayList<Integer> qsort() {
		qs( 0, positionList.size() - 1);
		return indexList;
	}
	
	private void qs(int left, int right) {
		int i, j;
		float pivot;
		Integer temp;
		i = left;
		j = right;

		r.setPosition( 0, 0 );
		pivot = accessor((left + right) / 2);
		
		do {
			while (accessor(i) < pivot && (i < right)) { i++; }
			
			while ((pivot < accessor(j)) && (j > left)) { j--; }
				
			if (i <= j) {
				temp = indexList.get(i);
				indexList.set(i, indexList.get(j));
				indexList.set(j, temp);
				i++;
				j--;
			}
		}  while (i <= j);
			
		if (left < j) {
			qs (left, j);
		}
			
		if (i < right) {
			qs (i, right);
		}
	}	
	
	float accessor(int i) {
		r.setPosition(positionList.get(indexList.get(i)).y, 0);
		r.setPosition(positionList.get(indexList.get(i)).x, 1);
		return r.get().getRealFloat();
	}
}
