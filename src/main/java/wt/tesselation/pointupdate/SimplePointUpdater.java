package wt.tesselation.pointupdate;

import java.util.Collection;

import net.imglib2.RealPoint;

public class SimplePointUpdater implements PointUpdater
{
	@Override
	public void updatePoints(
			final RealPoint p,
			final Collection< RealPoint > allpoints,
			final double dx, final double dy )
	{
		p.setPosition( p.getDoublePosition( 0 ) + dx, 0 );
		p.setPosition( p.getDoublePosition( 1 ) + dy, 1 );
	}
}
