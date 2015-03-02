package wt.tesselation.pointupdate;

import java.util.Collection;

import net.imglib2.RealPoint;
import net.imglib2.util.Util;

public class DistancePointUpdater implements PointUpdater
{
	protected double[] sigma;

	public DistancePointUpdater( final double sigma )
	{
		double[] sigmaTmp = Util.createGaussianKernel1DDouble( sigma, false );

		this.sigma = new double[ sigmaTmp.length / 2 + 1 ];

		// take only the second half [ 1 ... 0 ]
		for ( int i = sigmaTmp.length / 2; i < sigmaTmp.length; ++i )
			this.sigma[ i - sigmaTmp.length / 2 ] = sigmaTmp[ i ];
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
				p.setPosition( p.getDoublePosition( 0 ) + dx * sigma[ dist ], 0 );
				p.setPosition( p.getDoublePosition( 1 ) + dy * sigma[ dist ], 1 );
			}
		}
	}

	private static final double dist( final RealPoint p1, final RealPoint p2 )
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
