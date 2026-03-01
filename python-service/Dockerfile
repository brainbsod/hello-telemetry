# Use the official Python image from the Docker Hub
FROM python:3.13-slim

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy the requirements file into the container
COPY requirements.txt ./

# Install the required packages
RUN pip install --no-cache-dir -r requirements.txt

# Run opentelemetry-bootstrap to install instrumentation packages 
RUN opentelemetry-bootstrap -a install

# Copy the rest of the application code into the container
COPY . .

# Expose the port that the application will run on
EXPOSE 5000

# Set the environment variable for OpenTelemetry logging auto-instrumentation 
ENV OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED=true
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://ht-otel-collector:4317

# Define the command to run the application
CMD ["opentelemetry-instrument", "--service_name", "python-service", "python", "compute.py"]