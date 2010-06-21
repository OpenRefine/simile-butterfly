
var html = "text/html";
var encoding = "UTF-8";

/*
 *  This optional function is invoked from the module's init() Java function.
 */
function init() {
    // Packages.java.lang.System.err.println("Initializing by script");
}

/*
 * This is the function that is invoked by Butterfly
 */
function process(path, request, response) {

	var level = butterfly.getMountPoint().getMountPoint().replace("/","");
	
	var descs = {
		"" : "Home",
		level : "Samples",
		"dhtml" : "DHTML"
	}

	var context = {};
	context.paths = butterfly.makePath(request.pathInfo, descs);
	
	if (path == "dhtml/") {
		send(request, response, "DHTML Samples", "dhtml/index.vt", context);

	} else if (path == "dhtml/slideshow") {
	    context.headers = "dhtml/slideshow.headers.vt";
	    send(request, response, "jQuery Slideshow", "dhtml/slideshow.vt",context);

	} else if (path == "simple") {
		var items = new Packages.java.util.ArrayList();
	    items.add("1");
	    items.add("2");
	    items.add("3");
	    items.add("4");
	    items.add("5");
	
		context.items = items;           
	    send(request, response, "Simple Templating","simple.vt", context);

	} else if (path == "" || path.charAt(path.length) == "/") {
	    send(request, response, "Some Butterfly Examples", path + "index.vt", context);
	    
	} else if (path == "wirings.js") {
	    var wirings = butterfly.getWirings(request);
	    butterfly.sendString(
	        request, 
	        response, 
	        "var wirings = " + butterfly.toJSONString(wirings) + ";", 
	        encoding, 
	        "text/javascript"
	    );
	}
}

function send(request, response, title, template, context) {
    context.title = title;
    context.body = template;
    butterfly.sendTextFromTemplate(request, response, context, "template.vt", encoding, html);
}
	