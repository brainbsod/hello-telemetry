import logging
from flask import Flask, request, jsonify

# OpenTelemetry 
from opentelemetry import metrics,trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.semconv.resource import ResourceAttributes
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

# Create a Resource with the service.name attribute
resource = Resource.create({ResourceAttributes.SERVICE_NAME: "python-service"})


# Metrics
meter = metrics.get_meter(__name__)
compute_request_count = meter.create_counter(name='app_compute_request_count', description="Counts the requests to compute-service",unit='1')

# Traces
tracer = trace.get_tracer(__name__)

# Logs
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

app = Flask(__name__)

@app.route('/compute_average_age', methods=['POST'])
def compute_average_age():        
    # Increment compute counter
    compute_request_count.add(1)
    
    # Extract context
    # ctx = TraceContextTextMapPropagator().extract(request.headers)    

    # Start a new span
    with tracer.start_as_current_span("ComputeSpan"):
        logger.info("Average compute in progress")        

        # Process the request data
        data = request.json['data']
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        
        # Extract ages from the data
        ages = [item['age'] for item in data if 'age' in item]
        if not ages:
            return jsonify({'error': 'No age data available'}), 400
        
        # Compute the average age
        average_age = round(sum(ages) / len(ages), 1)        

        return jsonify({'average_age': average_age})
        
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)