package wt.tessellation.pointupdate;

import java.util.Collection;

import net.imglib2.RealPoint;
import net.imglib2.util.Util;

public class DistancePointUpdater implements PointUpdater
{
	protected double[] sigma;

	public DistancePointUpdater( final double sigma )
	{
		this.sigma = sigmas( sigma, false );
	}

	@Override
	public void updatePoints(
			final RealPoint p,
			final Collection< RealPoint > allpoints,
			final double dx, final double dy )
	{
		// otherwise could be updated in between
		final RealPoint reference = new RealPoint( p );

		for ( final RealPoint rp : allpoints )
		{
			final int dist = (int)Math.round( dist( rp, reference ) );

			if ( dist < sigma.length )
			{
				rp.setPosition( rp.getDoublePosition( 0 ) + dx * sigma[ dist ], 0 );
				rp.setPosition( rp.getDoublePosition( 1 ) + dy * sigma[ dist ], 1 );
			}
		}
	}

	public static double[] sigmas( final double sigma, final boolean normalize )
	{
		double[] sigmaTmp = Util.createGaussianKernel1DDouble( sigma, false );
		double[] sigmas = new double[ sigmaTmp.length / 2 + 1 ];

		// take only the second half [ 1 ... 0 ]
		for ( int i = sigmaTmp.length / 2; i < sigmaTmp.length; ++i )
			sigmas[ i - sigmaTmp.length / 2 ] = sigmaTmp[ i ];

		if ( normalize )
		{
			double sum = 0;
			for ( final double value : sigmas )
				sum += value;

			for ( int i = 0; i < sigmas.length; ++i )
				sigmas[ i ] /= sum;
		}

		return sigmas;
	}

	public static final double dist( final RealPoint p1, final RealPoint p2 )
	{
		final double x1 = p1.getDoublePosition( 0 );
		final double x2 = p2.getDoublePosition( 0 );
		final double y1 = p1.getDoublePosition( 1 );
		final double y2 = p2.getDoublePosition( 1 );

		final double dx = x2 - x1;
		final double dy = y2 - y1;
		
		return Math.sqrt( dx*dx + dy*dy );
	}
}
