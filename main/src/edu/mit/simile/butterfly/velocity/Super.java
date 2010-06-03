package edu.mit.simile.butterfly.velocity;

import java.io.IOException;
import java.io.Writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.velocity.Template;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.directive.InputBase;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.parser.node.SimpleNode;

/**
 * This class implements a Butterfly-special Velocity command "#super()" that allows
 * template to invoke the parent that they have overloaded. This works only
 * with modules extending one another and it's useful for template delegation.
 */
public class Super extends InputBase {

    final static private Logger _logger = LoggerFactory.getLogger("Velocity.super");
    
    @Override
    public String getName() {
        return "super";
    }

    @Override
    public int getType() {
        return LINE;
    }

    @Override
    public boolean render(InternalContextAdapter context, Writer writer, Node node) 
    throws IOException, ResourceNotFoundException, ParseErrorException, MethodInvocationException {
        
        // avoid rendering if no longer allowed (after a stop)
        if (!context.getAllowRendering()) {
            return true;
        }
        
        String template = context.getCurrentTemplateName();
        _logger.debug("Injecting parent of {}", template);

        // decorate the template so that the ButterflyModule understands it has
        // to look for an overloaded resource first. This is hacky, I know, but
        // unfortunately I see no other way of signaling such behavior to the 
        // resource loader via Velocity's own conduits.
        template = "@@" + template + "@@";
        
        // see if we have exceeded the configured depth.
        // If it isn't configured, put a stop at 20 just in case.

        Object[] templateStack = context.getTemplateNameStack();

        if (templateStack.length >= rsvc.getInt(RuntimeConstants.PARSE_DIRECTIVE_MAXDEPTH, 20)) {
            StringBuffer path = new StringBuffer();

            for (int i = 0; i < templateStack.length; ++i) {
                path.append(" > ");
                path.append(templateStack[i]);
            }

            _logger.error("Max recursion depth reached (" + templateStack.length + ")" + " File stack:" + path);
            return false;
        }

        // now use the Runtime resource loader to get the template

        Template t = null;

        try {
            t = rsvc.getTemplate(template, getInputEncoding(context));
        } catch (ResourceNotFoundException rnfe) {
            // the arg wasn't found. Note it and throw
            _logger.error("#inject(): cannot find template '" + template + "', called from template "
                    + context.getCurrentTemplateName() + " at (" + getLine() + ", " + getColumn() + ")");
            throw rnfe;
        } catch (ParseErrorException pee) {
            // the arg was found, but didn't parse - syntax error
            _logger.error("#inject(): syntax error in #inject()-ed template '" + template + "', called from template "
                    + context.getCurrentTemplateName() + " at (" + getLine() + ", " + getColumn() + ")");
            throw pee;
        } catch (Exception e) {
            _logger.error("#inject(): arg = " + template + ".  Exception: " + e);
            return false;
        }

        // and render it
        try {
            context.pushCurrentTemplateName(template);
            ((SimpleNode) t.getData()).render(context, writer);
        } catch (Exception e) {
            // if it's a MIE, it came from the render.... throw it...
            if (e instanceof MethodInvocationException) {
                throw (MethodInvocationException) e;
            }
            _logger.error("Exception rendering #inject(" + template + ")", e);
            return false;
        } finally {
            context.popCurrentTemplateName();
        }

        return true;
    }
}
