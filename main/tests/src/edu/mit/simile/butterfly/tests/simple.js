var a = 1;
var b = "blah";

var c = function(d) {
	for (var i = 0; i < 5; i++) {
		d();
	}
}

c(function() {
	a += b;
});

a;