package wt.quantify.localmaxima;

import java.util.Collection;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

public class SimpleLocalMaxima implements LocalMaxima< FloatType >
{
	@Override
	public Collection< RealPointValue< FloatType > > maxima( final Img< FloatType > img, final FloatType minValue )
	{
		return wt.tools.LocalMaxima.localMaxima( img, minValue );
	}
}
