
/*
 * This is the javascript code that gets injected in the controller scope
 * before the controller.process(...) function is called. This is useful
 * to define 'glue' and 'syntax sugar' functions.
 */
 
/*
 * Perform the JSON serialization in javascript and send the serialized string over
 */
Butterfly.prototype.sendJSON = function(request, response, object, wrap) {
	var json = butterfly.toJSONString(object);
	if (wrap) json = "<textarea>" + json + "</textarea>";
	this.sendString(request, response, json, "UTF-8", "text/plain");
}

/*
 * Perform the JSON serialization in javascript and send the serialized string over 
 * wrapped in a callback function call
 */
Butterfly.prototype.sendJSONP = function(request, response, object, callback) {
	var json = butterfly.toJSONString(object);
	this.sendString(request, response, callback + "(" + json + ");", "UTF-8", "text/plain");
}

/*
 * Obtain the request payload as a JSON object using the given optional filtering 
 * function to perform more specific data-type conversion
 */
Butterfly.prototype.getJSON = function(request) {
    return butterfly.parseJSON(this.getString(request));
}

/*
 * Return the module path wirings as a JSON object.
 */
Butterfly.prototype.getWirings = function(request) {
    var mounter = this.getMounter();
    var mountPaths = mounter.getMountPaths().toArray();
    var result = {};
    
    for each (var p in mountPaths) {
        var m = mounter.getModule(p, null);
        result[m.getName()] = p;
    }
    return result;
}

// ---------------------------------------------------------------------------------------

Butterfly.prototype.toJSONString = function(o) {
    if (o instanceof Object) {
        var str = s.object(o);
    } else if (o instanceof Array) {
        var str = s.array(o);
    } else {
        var str = o.toString();
    }
    return str;
};

var JSON_cleaning_RE = /"(\\.|[^"\\])*"/g;
var JSON_problematic_RE = /[^,:{}\[\]0-9.\-+Eaeflnr-u \n\r\t]/;

Butterfly.prototype.parseJSON = function (str) {
    try {
    	var s = new String(str); // NOTE(SM) this is to avoid nasty ambiguous java/javascript 
    	                         // mappings on the 'replace' call below
    	var cleaned_str = s.replace(JSON_cleaning_RE, "");
        if (!(JSON_problematic_RE.test(cleaned_str))) {
            return eval('(' + str + ')');
        }
    } catch (e) {
        butterfly.log(e);
    }
};

/*
 *  Adapted from http://www.json.org/json.js.
 */
    
var m = {
    '\b': '\\b',
    '\t': '\\t',
    '\n': '\\n',
    '\f': '\\f',
    '\r': '\\r',
    '"' : '\\"',
    '\\': '\\\\'
};

var s = {

    object: function (x) {
        if (x) {
            var h = x.hashCode;
            if (h && (typeof h == "function")) { // here we identify Java objects that are wrapped and made available to javascript
                var str = "" + x.toString(); // IMPORTANT: this converts a Java String object into a JavaScript string object!
                return s.string(str); 
            } else if (x instanceof Array || ("0" in x && "length" in x)) { // HACK: this tries to detect arrays
            	return s.array(x);
            } else {
                var a = ['{'], b, f, i, v;
                for (i in x) {
                    if (typeof i === 'string' && Object.prototype.hasOwnProperty.apply(x, [i])) {                    
                        v = x[i];
                        f = s[typeof v];
                        if (f) {
                            v = f(v);
                            if (typeof v == 'string') {
                                if (b) {
                                    a[a.length] = ',';
                                }
                                a.push(s.string(i), ':', v);
                                b = true;
                            }
                        }
                    }
                }
                a[a.length] = '}';
                return a.join('');
            }
        }
        return 'null';
    },

    array: function (x) {
        var a = ['['], b, f, i, l = x.length, v;
        for (i = 0; i < l; i += 1) {
            v = x[i];
            f = s[typeof v];
            if (f) {
                v = f(v);
                if (typeof v == 'string') {
                    if (b) {
                        a[a.length] = ',';
                    }
                    a[a.length] = v;
                    b = true;
                }
            }
        }
        a[a.length] = ']';
        return a.join('');
    },

    'null': function (x) {
        return "null";
    },

    'boolean': function (x) {
        return String(x);
    },

    number: function (x) {
        return isFinite(x) ? String(x) : 'null';
    },

    string: function (x) {
        if (/["\\\x00-\x1f]/.test(x)) {
            x = x.replace(/([\x00-\x1f\\"])/g, function(a, b) {
                var c = m[b];
                if (c) {
                    return c;
                }
                c = b.charCodeAt();
                return '\\u00' + Math.floor(c / 16).toString(16) + (c % 16).toString(16);
            });
        }
        return '"' + x + '"';
    }
};
 
// ---------------------------------------------------------------------------------------

String.prototype.trim = function() {
    return this.replace(/^\s+|\s+$/g, '');
};

String.prototype.startsWith = function(prefix) {
    return this.length >= prefix.length && this.substr(0, prefix.length) == prefix;
};

String.prototype.endsWith = function(suffix) {
    return this.length >= suffix.length && this.substr(this.length - suffix.length) == suffix;
};

String.substitute = function(s, objects) {
    var result = "";
    var start = 0;
    while (start < s.length - 1) {
        var percent = s.indexOf("%", start);
        if (percent < 0 || percent == s.length - 1) {
            break;
        } else if (percent > start && s.charAt(percent - 1) == "\\") {
            result += s.substring(start, percent - 1) + "%";
            start = percent + 1;
        } else {
            var n = parseInt(s.charAt(percent + 1));
            if (isNaN(n) || n >= objects.length) {
                result += s.substring(start, percent + 2);
            } else {
                result += s.substring(start, percent) + objects[n].toString();
            }
            start = percent + 2;
        }
    }
    
    if (start < s.length) {
        result += s.substring(start);
    }
    return result;
};
