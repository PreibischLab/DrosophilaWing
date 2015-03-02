
//makeRectangle(15, 285, 974, 492);

setBatchMode( true );

start = 5;
inc = 2.5;

j = 0;
for ( i = 1; i <= 4796; i++)
{
	selectWindow("Untitled");

    potenz = 0;
	for ( k = 1; k <= 100000; k = k*2 )
	{
		if ( i < start*k )
		{
			j = j+round(pow(inc,potenz));
			break;
		}
		potenz++;
	}
	print( i + " < " + (start*k) + " -- j = j + " + round(pow(inc,potenz)) + " >>> " + j );

	if ( j > 4796 )
	{
		print( "last slice = " + (i-1) );
		break;
	}
	setSlice( j );
	run("Copy");
	selectWindow("Untitled-1");
	setSlice(i);
	run("Paste");
}
print( "last slice = " + i );