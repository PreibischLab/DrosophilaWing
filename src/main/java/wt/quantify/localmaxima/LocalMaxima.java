package wt.quantify.localmaxima;

import java.util.Collection;

import net.imglib2.img.Img;

public interface LocalMaxima< T >
{
	Collection< RealPointValue< T > > maxima( final Img< T > img, final T minValue );
}
