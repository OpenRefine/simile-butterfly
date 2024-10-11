
/*
 * This is the javascript code that gets injected in the controller scope
 * before the controller.process(...) function is called. This is useful
 * to define 'glue' and 'syntax sugar' functions.
 */
 
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
